package org.example.project.core.utils

import androidx.compose.runtime.Composable
import org.example.project.core.domain.notifications.InterestNotice

interface InterestNotifier {
    val enabled: Boolean
    suspend fun preparePush(ownerId: String): String
    fun requestPermission()
    fun notify(notice: InterestNotice): Boolean
}
@Composable expect fun rememberInterestNotifier(): InterestNotifier
expect fun invalidateInterestNotifications()
expect suspend fun revokeInterestPushRegistration()
