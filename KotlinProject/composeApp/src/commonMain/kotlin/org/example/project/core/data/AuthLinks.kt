package org.example.project.core.data

/** Public destinations only. Incoming links carry neither verification material nor sessions. */
object AuthLinks {
    const val CONFIRMATION = "https://app.tachegandoapp.com.br/auth/confirm"
    const val RECOVERY = "https://app.tachegandoapp.com.br/auth/recovery"
    const val APP_ENTRY = "https://app.tachegandoapp.com.br/auth/open"

    // Exact canonical match rejects tokens, fragments, redirects, alternate ports and paths.
    fun isAppEntry(value: String?): Boolean = value == APP_ENTRY
}
