package org.example.project.core.auth

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import org.example.project.core.data.ConfirmationResender
import org.example.project.core.utils.UiState

class ConfirmationResenderTest {
    @Test fun neutralResponseDoesNotClaimDeliveryAndTrimsAddress() = runTest {
        var sent = ""
        val result = ConfirmationResender({ sent = it }).resend("  user@example.test ")
        assertEquals("user@example.test", sent)
        assertTrue((result as UiState.Success).data.startsWith("Se houver"))
    }
    @Test fun changingAddressCannotBypassCooldownAndBoundaryAllowsRetry() = runTest {
        var now = 0L; var sends = 0
        val sender = ConfirmationResender({ sends++ }, { now })
        assertIs<UiState.Success<String>>(sender.resend("one@example.test"))
        assertIs<UiState.Error>(sender.resend("two@example.test"))
        now = 59_999; assertIs<UiState.Error>(sender.resend("one@example.test"))
        now = 60_000; assertIs<UiState.Success<String>>(sender.resend("one@example.test"))
        assertEquals(2, sends)
    }
    @Test fun providerAndNetworkFailureNeverClaimEmailSentOrExposeAccount() = runTest {
        val messages = listOf("user not found secret=abc", "email already confirmed", "network timeout").map { error ->
            (ConfirmationResender({ throw IllegalStateException(error) }).resend("user@example.test") as UiState.Error).message
        }
        assertEquals(1, messages.distinct().size)
        assertFalse(messages.first().contains("secret"))
    }
    @Test fun invalidAddressNeverCallsAuth() = runTest {
        var calls = 0
        assertIs<UiState.Error>(ConfirmationResender({ calls++ }).resend("bad"))
        assertEquals(0, calls)
    }
}
