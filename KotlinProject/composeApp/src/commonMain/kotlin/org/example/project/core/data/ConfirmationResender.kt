package org.example.project.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import org.example.project.core.utils.UiState

/** Global cooldown prevents bypass by editing the email field. Backend rate limits remain authoritative. */
class ConfirmationResender(
    private val send: suspend (String) -> Unit,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    private val mutex = Mutex()
    private var lastAttempt: Long? = null
    suspend fun resend(email: String): UiState<String> = mutex.withLock {
        AuthValidation.email(email)?.let { return@withLock UiState.Error(it) }
        val instant = now()
        if (lastAttempt?.let { instant - it < 60_000 } == true) return@withLock UiState.Error("Aguarde um minuto antes de solicitar novamente.")
        lastAttempt = instant
        try {
            send(email.trim())
            UiState.Success("Se houver um cadastro aguardando confirmação, você receberá as instruções por email. Confira também o spam.")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { UiState.Error("Não foi possível processar o reenvio. Aguarde e tente novamente; confira sua conexão.") }
    }
}
