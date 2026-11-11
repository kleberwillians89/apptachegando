package org.example.project.core.data

import kotlinx.coroutines.CancellationException
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import org.example.project.core.utils.UiState

object AuthRepository {
    suspend fun signUp(
        emailValue: String,
        passwordValue: String,
        nameValue: String? = null,
        roleValue: String? = null,
        businessNameValue: String? = null,
        phoneValue: String? = null
    ): UiState<Unit> {
        AuthValidation.registration(emailValue, passwordValue, nameValue, roleValue)?.let { return UiState.Error(it) }
        return try {
            supabase.auth.signUpWith(Email, redirectUrl = AuthLinks.CONFIRMATION) {
                email = emailValue.trim()
                password = passwordValue
                if (nameValue != null || roleValue != null || businessNameValue != null || phoneValue != null) {
                    data = kotlinx.serialization.json.buildJsonObject {
                        put("registration_contract", kotlinx.serialization.json.JsonPrimitive("web-v1"))
                        if (nameValue != null) put("name", kotlinx.serialization.json.JsonPrimitive(nameValue))
                        if (nameValue != null) put("full_name", kotlinx.serialization.json.JsonPrimitive(nameValue))
                        if (businessNameValue != null) put("business_name", kotlinx.serialization.json.JsonPrimitive(businessNameValue))
                        if (phoneValue != null) put("phone", kotlinx.serialization.json.JsonPrimitive(phoneValue))
                        if (roleValue != null) put("role", kotlinx.serialization.json.JsonPrimitive(roleValue))
                    }
                }
            }
            UiState.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            UiState.Error(handleError(e))
        }
    }

    suspend fun signIn(emailValue: String, passwordValue: String): UiState<Unit> {
        AuthValidation.login(emailValue, passwordValue)?.let { return UiState.Error(it) }
        return try {
            supabase.auth.signInWith(Email) {
                email = emailValue.trim()
                password = passwordValue
            }
            UiState.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            UiState.Error(handleError(e))
        }
    }

    suspend fun signInAndResolveRole(email: String, password: String): UiState<String> =
        resolveAccountSignIn(authenticate = { signIn(email, password) }, currentOwner = { currentUser()?.id })

    suspend fun signOut(): UiState<Unit> {
        return try {
            org.example.project.core.utils.invalidateInterestNotifications()
            // Local invalidation happens first even if offline. Never block logout on push cleanup.
            try { org.example.project.core.utils.revokeInterestPushRegistration() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Server binding expiry/session validation remains required. */ }
            supabase.auth.signOut()
            UiState.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            UiState.Error("Erro ao sair. Tente novamente.")
        }
    }

    /** Only used for an incoming public App Link, never imports a browser session. */
    suspend fun awaitStoredSession() {
        if (GeneratedAppConfiguration.supabaseKey.isNotBlank()) supabase.auth.awaitInitialization()
    }

    fun currentUser() = if (GeneratedAppConfiguration.supabaseKey.isBlank()) null else supabase.auth.currentUserOrNull()

    suspend fun resolveCurrentUserRole(): String {
        val user = currentUser() ?: error("Entre na sua conta novamente.")
        return AccountProfileRepository().roleFor(user.id).also { check(currentUser()?.id == user.id) }
    }

    private val confirmationResender = ConfirmationResender(send = { email ->
        supabase.auth.resendEmail(io.github.jan.supabase.auth.OtpType.Email.SIGNUP, email)
    })
    suspend fun resendConfirmation(email: String): UiState<String> = confirmationResender.resend(email)

    suspend fun resetPassword(emailValue: String): UiState<Unit> {
        AuthValidation.email(emailValue)?.let { return UiState.Error(it) }
        return try {
            supabase.auth.resetPasswordForEmail(emailValue.trim(), redirectUrl = AuthLinks.RECOVERY)
            UiState.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            UiState.Error(handleError(e))
        }
    }

    private fun handleError(e: Exception): String =
        if (GeneratedAppConfiguration.supabaseKey.isBlank()) {
            "Configure o Supabase local antes de entrar ou cadastrar uma conta."
        } else authErrorMessage(e.message)
}
