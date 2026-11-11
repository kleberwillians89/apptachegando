package org.example.project.core.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.example.project.core.domain.notifications.InterestNotice

@Composable actual fun rememberInterestNotifier(): InterestNotifier = remember {
    object : InterestNotifier {
        override val enabled = false
        override suspend fun preparePush(ownerId: String) = "Push indisponível nesta plataforma"
        override fun requestPermission() = Unit
        override fun notify(notice: InterestNotice) = false
    }
}
actual fun invalidateInterestNotifications() = Unit

actual suspend fun revokeInterestPushRegistration() = Unit
