package org.example.project.core.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.example.project.core.data.AuthValidation
import org.example.project.core.data.authErrorMessage

class AuthValidationTest {
    @Test fun malformedEmailNeverReachesAuth() {
        for (value in listOf("", "a", "a@", "@b.com", "a@@b.com", "a b@c.com", "a@b", "a@b.com\nx")) {
            assertNotNull(AuthValidation.email(value), value)
        }
        assertNull(AuthValidation.email("  cliente+teste@exemplo.com.br  "))
    }

    @Test fun loginDoesNotApplySignupPasswordPolicyToExistingAccounts() {
        assertNull(AuthValidation.login("a@b.com", "123"))
        assertNotNull(AuthValidation.login("a@b.com", ""))
    }

    @Test fun signupAllowsBothPersonasWithoutBusinessDocument() {
        for (role in listOf("consumer", "vendor")) {
            assertNull(AuthValidation.registration("a@b.com", "12345678", "Ana", role))
        }
        assertNotNull(AuthValidation.registration("a@b.com", "12345678", " ", "vendor"))
        assertNotNull(AuthValidation.registration("a@b.com", "123", "Ana", "vendor"))
        assertNotNull(AuthValidation.registration("a@b.com", "x".repeat(129), "Ana", "consumer"))
        assertNotNull(AuthValidation.registration("a@b.com", "12345678", "x".repeat(161), "consumer"))
        assertNotNull(AuthValidation.registration("a@b.com", "12345678", "Ana", "admin"))
    }

    @Test fun unconfirmedEmailIsActionableInsteadOfInvalidCredentials() {
        for (error in listOf("Email not confirmed", "email_not_confirmed")) {
            assertTrue(authErrorMessage(error).startsWith("Confirme seu email"))
        }
    }

    @Test fun errorsNeverExposeBackendPayload() {
        val raw = "unexpected server error token=private-value"
        assertEquals("Não foi possível concluir a solicitação. Tente novamente.", authErrorMessage(raw))
        assertEquals(authErrorMessage(raw), authErrorMessage(null))
        assertEquals("Email ou senha incorretos.", authErrorMessage("invalid_credentials"))
        assertTrue(authErrorMessage("over_email_send_rate_limit").contains("Aguarde"))
        assertTrue(authErrorMessage("Connection timeout").contains("internet"))
    }
}
