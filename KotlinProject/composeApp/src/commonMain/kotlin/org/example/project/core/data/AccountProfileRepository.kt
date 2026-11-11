package org.example.project.core.data

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountProfile(
    @SerialName("user_id") val userId: String,
    val role: String,
    @SerialName("vendor_id") val vendorId: String? = null
)

/** Metadata is deliberately absent. The server resolves the authenticated account's persisted link. */
class AccountProfileRepository(private val load: suspend () -> AccountProfile = {
    supabase.postgrest.rpc("get_my_account_profile").decodeAs<AccountProfile>()
}) {
    suspend fun roleFor(userId: String): String {
        val profile = load()
        check(profile.userId == userId) { "O perfil recebido pertence a outra sessão." }
        return when (profile.role) {
            "vendor" -> { check(!profile.vendorId.isNullOrBlank()); "vendor" }
            "consumer" -> { check(profile.vendorId == null); "consumer" }
            "vendor_incomplete" -> throw ProfessionalProfileIncomplete()
            else -> error("Perfil indisponível.")
        }
    }
}

class ProfessionalProfileIncomplete : IllegalStateException(
    "Seu cadastro profissional ainda não foi concluído. Conclua o perfil pelo site ou pela opção abaixo antes de continuar."
)

suspend fun completeProfessionalProfile(name: String, business: String, category: String): AccountProfile {
    require(name.trim().length in 1..160 && business.trim().length in 1..160)
    require(category in org.example.project.core.domain.models.discoveryCategories)
    val owner = AuthRepository.currentUser()?.id ?: error("Entre na sua conta novamente.")
    val profile = supabase.postgrest.rpc("complete_my_profile", kotlinx.serialization.json.buildJsonObject {
        put("p_kind", kotlinx.serialization.json.JsonPrimitive("vendor"))
        put("p_name", kotlinx.serialization.json.JsonPrimitive(name.trim()))
        put("p_business_name", kotlinx.serialization.json.JsonPrimitive(business.trim()))
        put("p_category", kotlinx.serialization.json.JsonPrimitive(category))
    }).decodeAs<AccountProfile>()
    check(profile.userId == owner && AuthRepository.currentUser()?.id == owner && profile.role == "vendor" && !profile.vendorId.isNullOrBlank())
    return profile
}

/** Shared orchestration used by the real SDK login and controlled integration tests. */
internal suspend fun resolveAccountSignIn(
    authenticate: suspend () -> org.example.project.core.utils.UiState<Unit>,
    currentOwner: () -> String?,
    profiles: AccountProfileRepository = AccountProfileRepository()
): org.example.project.core.utils.UiState<String> = when (val auth = authenticate()) {
    is org.example.project.core.utils.UiState.Success -> try {
        val owner = currentOwner() ?: error("Sessão indisponível")
        val role = profiles.roleFor(owner)
        check(currentOwner() == owner)
        org.example.project.core.utils.UiState.Success(role)
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (incomplete: ProfessionalProfileIncomplete) { org.example.project.core.utils.UiState.Error(incomplete.message ?: "Conclua seu perfil profissional.") }
    catch (_: Exception) { org.example.project.core.utils.UiState.Error("Não foi possível carregar seu perfil. Tente entrar novamente.") }
    is org.example.project.core.utils.UiState.Error -> auth
    org.example.project.core.utils.UiState.Loading -> org.example.project.core.utils.UiState.Loading
}
