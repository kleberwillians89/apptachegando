package org.example.project.core.auth

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import org.example.project.core.data.*
import org.example.project.core.utils.UiState

/** Controlled Auth/profile transports, not a Supabase server and not remote QA accounts. */
class WebAccountIntegrationTest {
    private val owner = "10000000-0000-4000-8000-000000000001"
    @Test fun confirmedWebConsumerEntersWithoutBrowserTokenTransfer() = runTest {
        val result = resolveAccountSignIn({ UiState.Success(Unit) }, { owner }, AccountProfileRepository { AccountProfile(owner, "consumer") })
        assertEquals("consumer", (result as UiState.Success).data)
    }
    @Test fun confirmedProvisionedWebVendorEntersProfessionalPanel() = runTest {
        val result = resolveAccountSignIn({ UiState.Success(Unit) }, { owner }, AccountProfileRepository { AccountProfile(owner, "vendor", "30000000-0000-4000-8000-000000000001") })
        assertEquals("vendor", (result as UiState.Success).data)
    }
    @Test fun unconfirmedAccountNeverQueriesOrAssumesRole() = runTest {
        var reads = 0
        val result = resolveAccountSignIn({ UiState.Error(authErrorMessage("email_not_confirmed")) }, { null }, AccountProfileRepository { reads++; error("must not query") })
        assertTrue((result as UiState.Error).message.startsWith("Confirme seu email"))
        assertEquals(0, reads)
    }
    @Test fun incompleteProfessionalIsExplicitAndNeverConsumer() = runTest {
        val result = resolveAccountSignIn({ UiState.Success(Unit) }, { owner }, AccountProfileRepository { AccountProfile(owner, "vendor_incomplete") })
        assertTrue((result as UiState.Error).message.contains("cadastro profissional"))
    }
    @Test fun failedQueryNeverBecomesConsumerSuccess() = runTest {
        val result = resolveAccountSignIn({ UiState.Success(Unit) }, { owner }, AccountProfileRepository { error("network failure") })
        assertIs<UiState.Error>(result)
    }
    @Test fun foreignProfileAndMalformedVendorFailClosed() = runTest {
        for (profile in listOf(AccountProfile("another-user", "consumer"), AccountProfile(owner, "vendor"), AccountProfile(owner, "admin"))) {
            assertIs<UiState.Error>(resolveAccountSignIn({ UiState.Success(Unit) }, { owner }, AccountProfileRepository { profile }))
        }
    }
    @Test fun logoutDuringQueryCannotNavigateWithStaleIdentity() = runTest {
        var current: String? = owner
        val result = resolveAccountSignIn({ UiState.Success(Unit) }, { current }, AccountProfileRepository { current = null; AccountProfile(owner, "consumer") })
        assertIs<UiState.Error>(result)
    }
    @Test fun tokenRenewalForSameOwnerRetainsPersistedRole() = runTest {
        var accessGeneration = 1
        val repository = AccountProfileRepository { check(accessGeneration > 0); AccountProfile(owner, "vendor", "vendor-id") }
        assertEquals("vendor", repository.roleFor(owner))
        accessGeneration++ // Controlled credential rotation; real SDK refresh still requires real Auth validation.
        assertEquals("vendor", repository.roleFor(owner))
    }
    @Test fun cancellationNeverBecomesProfileError() = runTest {
        assertFailsWith<CancellationException> {
            resolveAccountSignIn({ UiState.Success(Unit) }, { owner }, AccountProfileRepository { throw CancellationException() })
        }
    }
}
