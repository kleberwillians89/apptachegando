package org.example.project.core.utils

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.example.project.MainActivity
import org.example.project.core.data.AuthRepository
import org.example.project.core.domain.notifications.InterestNotice
import org.example.project.core.domain.notifications.valid
import java.util.UUID

/** Android channel owns sound/vibration/DND. No direct sound or vibration bypass. */
object AndroidInterestNotices {
    const val CHANNEL = "route_interests"
    private var application: Context? = null
    fun initialize(context: Context) { application = context.applicationContext }
    private fun preferences(context: Context) = context.getSharedPreferences("interest_notices", Context.MODE_PRIVATE)
    @Synchronized fun invalidate() {
        val context = application ?: return
        InterestFirebasePush.invalidateLocal(context)
        val blockedOwner = AuthRepository.currentUser()?.id ?: preferences(context).getString("owner", null)
        preferences(context).edit().clear().putString("blocked_owner", blockedOwner).commit()
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }
    @Synchronized fun activateOwner(context: Context, owner: String) {
        if (AuthRepository.currentUser()?.id == owner) preferences(context).edit().remove("blocked_owner").commit()
    }
    fun channel(context: Context): NotificationManager {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Interesses na rota", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Novos interesses elegíveis durante sua jornada"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 80, 60, 80)
                setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            })
        }
        return manager
    }
    fun enabled(context: Context): Boolean {
        val manager = channel(context)
        return (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 26 || manager.getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE)
    }
    @Synchronized fun notify(context: Context, notice: InterestNotice): Boolean {
        if (!notice.valid() || AuthRepository.currentUser()?.id != notice.ownerId || !enabled(context)) return false
        val prefs = preferences(context)
        if (prefs.getString("blocked_owner", null) == notice.ownerId) return false
        if (prefs.getString("owner", null) != notice.ownerId) {
            channel(context).cancelAll()
            prefs.edit().clear().commit()
            prefs.edit().putString("owner", notice.ownerId).putString("binding", UUID.randomUUID().toString()).commit()
        }
        val key = notice.routeId + ":" + notice.protocolId
        val ledgerKey = "delivered:" + notice.routeId
        val seen = prefs.getStringSet(ledgerKey, emptySet())!!.toMutableSet()
        if (key in seen || seen.size >= 2000) return false
        // Persist before notifying. A crash can lose an alert but must never replay old alerts.
        seen += key
        if (!prefs.edit().putStringSet(ledgerKey, seen).commit()) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "org.example.project.INTEREST"
            data = android.net.Uri.parse("tachegando-internal://interest/" + notice.ownerId + "/" + notice.routeId + "/" + notice.protocolId)
            putExtra("notice_owner", notice.ownerId); putExtra("notice_route", notice.routeId)
            putExtra("notice_protocol", notice.protocolId); putExtra("notice_binding", prefs.getString("binding", null))
        }
        val pending = PendingIntent.getActivity(context, key.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else Notification.Builder(context)
        builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Novo interesse na sua rota")
            .setContentText("Consulte a fila e a disponibilidade. Não é uma compra confirmada.")
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE)
        if (Build.VERSION.SDK_INT < 26) builder.setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
        return try { channel(context).notify(key, 1, builder.build()); true } catch (_: SecurityException) { false }
    }
    fun readTarget(context: Context, intent: Intent?): InterestNotice? {
        if (intent?.action != "org.example.project.INTEREST") return null
        val prefs = preferences(context)
        val binding = prefs.getString("binding", null) ?: return null
        if (binding != intent.getStringExtra("notice_binding")) return null
        return InterestNotice(intent.getStringExtra("notice_owner") ?: "", intent.getStringExtra("notice_route") ?: "", intent.getStringExtra("notice_protocol") ?: "")
            .takeIf { it.valid() && it.ownerId == prefs.getString("owner", null) }
    }
}

@Composable actual fun rememberInterestNotifier(): InterestNotifier {
    val context = LocalContext.current
    val enabledState = remember { mutableStateOf(AndroidInterestNotices.enabled(context)) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { enabledState.value = AndroidInterestNotices.enabled(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) enabledState.value = AndroidInterestNotices.enabled(context) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return remember(context, request) {
        object : InterestNotifier {
            override val enabled: Boolean get() = enabledState.value
            override suspend fun preparePush(ownerId: String): String {
                AndroidInterestNotices.activateOwner(context, ownerId)
                return InterestFirebasePush.prepare(context, ownerId)
            }
            override fun requestPermission() {
                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) request.launch(Manifest.permission.POST_NOTIFICATIONS)
                else {
                    val settings = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName))
                    context.startActivity(settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            override fun notify(notice: InterestNotice) = AndroidInterestNotices.notify(context, notice)
        }
    }
}
actual fun invalidateInterestNotifications() = AndroidInterestNotices.invalidate()

actual suspend fun revokeInterestPushRegistration() = InterestFirebasePush.revokePending()
