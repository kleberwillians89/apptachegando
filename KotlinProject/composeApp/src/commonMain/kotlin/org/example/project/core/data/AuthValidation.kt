package org.example.project.core.data

/** Input checks only. Identity and permissions are always verified by Auth/RLS. */
object AuthValidation {
    fun email(value: String): String? {
        val normalized = value.trim()
        return if (normalized.length > 254 ||
            !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(normalized)) {
            "Digite um email válido."
        } else null
    }

    fun login(email: String, password: String): String? =
        email(email) ?: if (password.isEmpty()) "Informe sua senha." else null

    fun registration(email: String, password: String, name: String?, role: String?): String? =
        email(email) ?: when {
            name.isNullOrBlank() -> "Informe seu nome."
            name.trim().length > 160 -> "Use um nome com até 160 caracteres."
            role !in listOf("consumer", "vendor") -> "Escolha Cliente ou Profissional."
            password.length !in 8..128 -> "Use uma senha com 8 a 128 caracteres."
            else -> null
        }
}

/** Never show raw backend errors (which may contain request details) in the UI. */
internal fun authErrorMessage(message: String?): String {
    val msg = message.orEmpty().lowercase()
    return when {
        "email not confirmed" in msg || "email_not_confirmed" in msg ->
            "Confirme seu email antes de entrar. Confira sua caixa de entrada e o spam."
        "invalid login credentials" in msg || "invalid_credentials" in msg -> "Email ou senha incorretos."
        "user already registered" in msg || "user_already_exists" in msg ->
            "Este email já está cadastrado. Entre ou recupere sua senha."
        "password should be" in msg || "weak_password" in msg -> "A senha não atende aos requisitos de segurança."
        "too many requests" in msg || "rate_limit" in msg ->
            "Você tentou muitas vezes. Aguarde alguns minutos e tente novamente."
        "network" in msg || "timeout" in msg || "host" in msg || "connection" in msg ->
            "Não foi possível conectar. Verifique sua internet e tente novamente."
        else -> "Não foi possível concluir a solicitação. Tente novamente."
    }
}
