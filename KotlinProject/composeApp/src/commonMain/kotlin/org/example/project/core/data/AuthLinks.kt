package org.example.project.core.data

/** Auth e-mail redirects use the official site. Never place tokens in app entry URLs. */
object AuthLinks {
    const val CONFIRMATION = "https://tachegandoapp.com.br/cadastro?modo=entrar"
    const val RECOVERY = "https://tachegandoapp.com.br/recuperar"
    // Reserved for Android App Links; do not use until the app subdomain has valid DNS/HTTPS.
    const val APP_ENTRY = "https://app.tachegandoapp.com.br/auth/open"

    // Exact canonical match rejects tokens, fragments, redirects, alternate ports and paths.
    fun isAppEntry(value: String?): Boolean = value == APP_ENTRY
}
