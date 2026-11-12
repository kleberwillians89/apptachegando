package org.example.project.core.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.example.project.core.data.AuthLinks

class AuthLinksTest {
    @Test fun emailLinksUseWorkingPublicWebsiteNotUnconfiguredSubdomain() {
        kotlin.test.assertEquals("https://tachegandoapp.com.br/cadastro?modo=entrar", AuthLinks.CONFIRMATION)
        kotlin.test.assertEquals("https://tachegandoapp.com.br/recuperar", AuthLinks.RECOVERY)
        assertFalse(AuthLinks.CONFIRMATION.contains("app.tachegandoapp.com.br"))
        assertFalse(AuthLinks.RECOVERY.contains("app.tachegandoapp.com.br"))
    }
    @Test fun onlyCanonicalPublicEntryIsAccepted() {
        assertTrue(AuthLinks.isAppEntry("https://app.tachegandoapp.com.br/auth/open"))
        for (value in listOf(null, "", "http://app.tachegandoapp.com.br/auth/open",
            "https://evil.example/auth/open", "https://app.tachegandoapp.com.br.evil.example/auth/open",
            "https://app.tachegandoapp.com.br@evil.example/auth/open",
            "https://app.tachegandoapp.com.br:444/auth/open", AuthLinks.CONFIRMATION, AuthLinks.RECOVERY,
            AuthLinks.APP_ENTRY + "?access_token=synthetic", AuthLinks.APP_ENTRY + "#refresh_token=synthetic",
            AuthLinks.APP_ENTRY + "?next=https://evil.example", AuthLinks.APP_ENTRY + "/")) {
            assertFalse(AuthLinks.isAppEntry(value))
        }
    }
}
