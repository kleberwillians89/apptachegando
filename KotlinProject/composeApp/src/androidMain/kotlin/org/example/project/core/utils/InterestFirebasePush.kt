package org.example.project.core.utils

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.android.gms.tasks.Task
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.example.project.core.data.AuthRepository
import org.example.project.core.data.GeneratedAppConfiguration
import org.example.project.core.data.supabase
import org.example.project.core.domain.notifications.InterestNotice
import org.example.project.core.domain.notifications.valid
import org.example.project.core.domain.notifications.canRegisterPush
import org.example.project.core.domain.notifications.pushBindingMatches
import org.example.project.core.domain.notifications.freshPushStamp
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Disabled unless Firebase public config AND separately installed backend contracts are declared.
 * No credentials/config are invented and no token/RPC is requested in the default QA build. */
object InterestFirebasePush {
    private var application: Context? = null
    private val localStateLock = Any()
    private val operations = kotlinx.coroutines.sync.Mutex()
    private fun prefs(context: Context) = context.getSharedPreferences("interest_push_device", Context.MODE_PRIVATE)
    private fun firebase(context: Context): FirebaseMessaging? {
        if (!GeneratedAppConfiguration.pushConfigured) return null
        application = context.applicationContext
        if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
            .setApplicationId(GeneratedAppConfiguration.firebaseAppId).setProjectId(GeneratedAppConfiguration.firebaseProjectId)
            .setApiKey(GeneratedAppConfiguration.firebaseApiKey).setGcmSenderId(GeneratedAppConfiguration.firebaseSenderId).build())
        return FirebaseMessaging.getInstance()
    }
    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
        addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    }
    suspend fun prepare(context: Context, owner: String): String {
        application = context.applicationContext
        if (!GeneratedAppConfiguration.pushConfigured) return "Push em segundo plano não configurado"
        if (!AndroidInterestNotices.enabled(context)) return "Permita notificações para habilitar push"
        return try {
            withTimeout(8_000) {
                check(AuthRepository.currentUser()?.id == owner)
                val fcm = firebase(context) ?: error("Push indisponível")
                fcm.isAutoInitEnabled = true
                val epoch = prefs(context).getLong("logout_epoch", 0)
                val token = fcm.token.awaitResult()
                register(context, owner, token, epoch, false)
            }
            "Dispositivo inscrito para push · entrega ainda requer teste real"
        } catch (cancelled: CancellationException) {
            if (cancelled is TimeoutCancellationException) "Não foi possível confirmar a inscrição push" else throw cancelled
        } catch (_: Exception) { "Não foi possível confirmar a inscrição push. A fila continua independente." }
    }
    private suspend fun register(context: Context, owner: String, token: String, epoch: Long, renewal: Boolean) {
        operations.lock()
        try {
            val p = prefs(context)
            val registration = synchronized(localStateLock) {
                check(AuthRepository.currentUser()?.id == owner)
                val existingOwner = if (p.getLong("binding_epoch", -1) == p.getLong("logout_epoch", 0)) p.getString("owner", null) else null
                check(canRegisterPush(owner, AuthRepository.currentUser()?.id, epoch, p.getLong("logout_epoch", 0), existingOwner, renewal))
                val installation = p.getString("installation", null) ?: UUID.randomUUID().toString()
                // Correlation only; authorization remains in the database with the signed JWT.
                val jwt = supabase.auth.currentSessionOrNull()?.accessToken ?: error("Sessão indisponível")
                val payload = android.util.Base64.decode(jwt.split('.')[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING).decodeToString()
                val sessionId = Json.parseToJsonElement(payload).jsonObject["session_id"]?.jsonPrimitive?.content ?: error("Sessão indisponível")
                UUID.fromString(sessionId)
                val same = existingOwner == owner && p.getString("token", null) == token && p.getString("session", null) == sessionId
                val wasConfirmed = same && p.getBoolean("confirmed", false)
                val version = if (same) p.getString("version", null)!! else UUID.randomUUID().toString()
                check(p.edit().putString("installation", installation).putString("owner", owner).putString("token", token)
                    .putString("version", version).putString("session", sessionId).putLong("binding_epoch", epoch).putBoolean("confirmed", wasConfirmed).commit())
                installation to version
            }
            val (installation, version) = registration
            val confirmation = supabase.postgrest.rpc("register_interest_push_device", buildJsonObject {
                put("p_installation_id", installation); put("p_token", token); put("p_binding_version", version)
            }).decodeAs<String>()
            synchronized(localStateLock) {
                check(confirmation == installation && AuthRepository.currentUser()?.id == owner)
                check(p.getLong("logout_epoch", 0) == epoch && p.getLong("binding_epoch", -1) == epoch && p.getString("owner", null) == owner && p.getString("version", null) == version)
                check(p.edit().putBoolean("confirmed", true).commit())
            }
        } finally { operations.unlock() }
    }
    fun invalidateLocal(context: Context) {
        application = context.applicationContext
        synchronized(localStateLock) {
            val p = prefs(context)
            p.edit().putLong("logout_epoch", p.getLong("logout_epoch", 0) + 1).putString("revoke_installation", p.getString("installation", null))
                .putString("revoke_version", p.getString("version", null)).remove("owner").remove("token").remove("version").remove("session").putBoolean("confirmed", false).commit()
        }
        if (GeneratedAppConfiguration.pushConfigured) runCatching { firebase(context)?.apply { isAutoInitEnabled = false; deleteToken() } }
    }
    suspend fun revokePending() {
        if (!GeneratedAppConfiguration.pushConfigured) return
        val p = application?.let(::prefs) ?: return
        val installation = p.getString("revoke_installation", null) ?: return
        val version = p.getString("revoke_version", null) ?: return
        withTimeoutOrNull(4_000) {
            val confirmation = supabase.postgrest.rpc("revoke_interest_push_device", buildJsonObject {
                put("p_installation_id", installation); put("p_binding_version", version)
            }).decodeAs<String>()
            check(confirmation == installation)
            if (p.getString("revoke_installation", null) == installation && p.getString("revoke_version", null) == version) p.edit().remove("revoke_installation").remove("revoke_version").commit()
        }
    }
    suspend fun renewed(context: Context, token: String) {
        if (!GeneratedAppConfiguration.pushConfigured) return
        AuthRepository.awaitStoredSession()
        val (owner, epoch) = synchronized(localStateLock) {
            val p = prefs(context)
            if (p.getLong("binding_epoch", -1) != p.getLong("logout_epoch", 0)) return
            (p.getString("owner", null) ?: return) to p.getLong("logout_epoch", 0)
        }
        if (AuthRepository.currentUser()?.id == owner && AndroidInterestNotices.enabled(context)) register(context, owner, token, epoch, true)
    }
    suspend fun receive(context: Context, data: Map<String, String>) {
        if (!GeneratedAppConfiguration.pushConfigured || data["type"] != "route_interest") return
        val notice = InterestNotice(data["owner_id"] ?: "", data["route_id"] ?: "", data["protocol_id"] ?: "")
        val version = data["binding_version"] ?: return
        val issued = data["issued_at_ms"]?.toLongOrNull() ?: return
        val expires = data["expires_at_ms"]?.toLongOrNull() ?: return
        val now = System.currentTimeMillis()
        if (!notice.valid() || !freshPushStamp(issued, expires, now)) return
        val p = prefs(context)
        fun bound() = synchronized(localStateLock) { p.getLong("binding_epoch", -1) == p.getLong("logout_epoch", 0) && p.getBoolean("confirmed", false) && p.getString("owner", null) == notice.ownerId && p.getString("version", null) == version }
        if (!bound()) return
        AuthRepository.awaitStoredSession()
        if (!pushBindingMatches(p.getString("owner", null), p.getString("version", null), AuthRepository.currentUser()?.id, notice.ownerId, version, p.getBoolean("confirmed", false))) return
        // Read-only authoritative check: cancelled/paused/stale/off-route must not notify.
        val eligible = supabase.postgrest.rpc("verify_interest_push", buildJsonObject {
            put("p_route_id", notice.routeId); put("p_intent_id", notice.protocolId)
            put("p_installation_id", p.getString("installation", null) ?: ""); put("p_binding_version", version)
        }).decodeAs<Boolean>()
        if (eligible && bound() && AuthRepository.currentUser()?.id == notice.ownerId) AndroidInterestNotices.notify(context, notice)
    }
}

/** FCM owns wake-up; no UI/Realtime collector, GPS service or foreground service is started. */
class InterestMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        AndroidInterestNotices.initialize(applicationContext)
        runCatching { runBlocking { withTimeout(8_000) { InterestFirebasePush.renewed(applicationContext, token) } } }
    }
    override fun onMessageReceived(message: RemoteMessage) {
        AndroidInterestNotices.initialize(applicationContext)
        runCatching { runBlocking { withTimeout(8_000) { InterestFirebasePush.receive(applicationContext, message.data) } } }
    }
}
