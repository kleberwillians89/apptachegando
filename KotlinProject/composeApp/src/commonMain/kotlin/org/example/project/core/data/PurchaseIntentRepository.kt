package org.example.project.core.data

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import org.example.project.core.utils.Location
import org.example.project.core.utils.UiState

@Serializable
data class PurchaseIntentReceipt(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    val status: String,
    @SerialName("expires_at") val expiresAt: String,
    val eligibility: String = "awaiting_availability"
)

object PurchaseIntentRepository {
    /** RLS remains authoritative; explicit owner/vendor filters also avoid mixing UI receipts. */
    suspend fun pendingForVendor(vendorId: String): UiState<PurchaseIntentReceipt?> = backendResult(
        "Não foi possível consultar sua intenção. Verifique a conexão."
    ) {
        val userId = AuthRepository.currentUser()?.id ?: error("Entre na sua conta.")
        supabase.postgrest["purchase_intents"].select(columns = Columns.list("id", "vendor_id", "status", "expires_at")) {
            filter {
                eq("consumer_id", userId)
                eq("vendor_id", vendorId)
                eq("status", "pending")
                gt("expires_at", Clock.System.now().toString())
            }
            order("created_at", Order.DESCENDING)
            limit(1)
        }.decodeSingleOrNull<PurchaseIntentReceipt>()?.let { state(it.id) }
    }

    private suspend fun state(id: String): PurchaseIntentReceipt = supabase.postgrest.rpc(
        "get_my_interest_state", buildJsonObject { put("p_intent_id", id) }
    ).decodeAs<PurchaseIntentReceipt>()

    suspend fun refresh(id: String): UiState<PurchaseIntentReceipt> = backendResult("Não foi possível atualizar o estado do interesse.") { state(id) }

    suspend fun cancel(id: String): UiState<Unit> = backendResult("Não foi possível cancelar. Verifique a conexão e a liberação do atendimento no servidor.") {
        check(AuthRepository.currentUser() != null)
        val confirmed = supabase.postgrest.rpc("cancel_purchase_intent", buildJsonObject { put("p_intent_id", id) }).decodeAs<String>()
        check(confirmed == id)
    }

    suspend fun createArrivalAlert(vendorId: String, location: Location): UiState<PurchaseIntentReceipt> = backendResult(
        "Não foi possível registrar. Confira a conexão e se o profissional está em uma jornada ativa."
    ) {
        val userId = AuthRepository.currentUser()?.id ?: error("Entre na sua conta.")
        val id = supabase.postgrest.rpc("create_purchase_intent", buildJsonObject {
            put("p_vendor_id", vendorId)
            put("p_latitude", location.latitude)
            put("p_longitude", location.longitude)
        }).decodeAs<String>()
        check(id.isNotBlank()) { "Servidor não retornou o identificador da intenção." }
        // Success requires reading the persisted result, not merely receiving HTTP 200.
        supabase.postgrest["purchase_intents"].select(columns = Columns.list("id", "vendor_id", "status", "expires_at")) {
            filter { eq("id", id); eq("vendor_id", vendorId); eq("consumer_id", userId) }
        }.decodeSingle<PurchaseIntentReceipt>().let { state(it.id) }
    }
}

/** Eligibility is a projection, never a sale, reservation or persisted workflow state. */
fun PurchaseIntentReceipt.conditionMessage(): String = when {
    status == "cancelled" || eligibility == "cancelled" -> "Interesse cancelado."
    status == "expired" || eligibility == "expired" -> "Interesse expirado."
    eligibility == "eligible" -> "Interesse registrado e elegível para a fila automática. Não confirma atendimento ou estoque."
    eligibility == "vendor_location_missing" -> "Interesse registrado. O profissional está sem localização recente; aguarde uma nova posição."
    eligibility == "outside_route" -> "Interesse registrado, mas sua posição está fora do percurso."
    eligibility == "behind_route" -> "Interesse registrado, mas o profissional já passou pela sua posição no percurso."
    eligibility == "ambiguous_route" -> "Interesse registrado. Sua posição é ambígua neste percurso; o atendimento ainda não é elegível."
    else -> "Interesse registrado, aguardando condições de atendimento."
}
