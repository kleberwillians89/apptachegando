package org.example.project.core.data

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.flow
import io.github.jan.supabase.realtime.realtime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Vendor(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val distance: String,
    val rating: String,
    @SerialName("review_count") val reviewCount: Int,
    @SerialName("is_online") val isOnline: Boolean,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val products: List<Product> = emptyList()
)

@Serializable
data class Product(
    val id: String = "",
    val name: String,
    val price: String = ""
)

@OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
object VendorRepository {
    val vendors = emptyList<Vendor>()

    const val PAGE_SIZE = 50
    private val summaryColumns = Columns.list("id", "name", "description", "category", "distance", "rating", "review_count", "is_online", "latitude", "longitude")

    suspend fun getVendors(page: Int = 0, query: String = ""): List<Vendor> {
        if (query.isNotBlank()) return searchCatalog(query, "", page)
        val bounds = pageBounds(page, PAGE_SIZE)
        return supabase.postgrest["vendor_catalog"].select(columns = summaryColumns) {
            order("id", Order.ASCENDING)
            range(bounds.first, bounds.last)
        }.decodeList<Vendor>()
    }

    suspend fun updateLocation(vendorId: String, latitude: Double, longitude: Double) {
        val userId = AuthRepository.currentUser()?.id ?: error("Entre na sua conta.")
        check(resolveRouteVendorId(userId) == vendorId) { "Vendedor inválido." }
        supabase.postgrest.rpc("update_vendor_location", kotlinx.serialization.json.buildJsonObject {
            put("p_latitude", kotlinx.serialization.json.JsonPrimitive(latitude))
            put("p_longitude", kotlinx.serialization.json.JsonPrimitive(longitude))
        })
    }

    suspend fun publishRouteLocation(userId: String, routeId: String, location: org.example.project.core.utils.Location, recordedAtMillis: Long, accuracyMeters: Double?) {
        check(AuthRepository.currentUser()?.id == userId) { "Usuário inválido." }
        if (!GeneratedAppConfiguration.pushBackendConfigured) {
            supabase.postgrest.rpc("update_vendor_location", kotlinx.serialization.json.buildJsonObject {
                put("p_latitude", kotlinx.serialization.json.JsonPrimitive(location.latitude))
                put("p_longitude", kotlinx.serialization.json.JsonPrimitive(location.longitude))
                if (accuracyMeters != null) put("p_accuracy_m", kotlinx.serialization.json.JsonPrimitive(accuracyMeters))
            })
            return
        }
        val confirmed = supabase.postgrest.rpc("publish_route_location", kotlinx.serialization.json.buildJsonObject {
            put("p_route_id", kotlinx.serialization.json.JsonPrimitive(routeId))
            put("p_latitude", kotlinx.serialization.json.JsonPrimitive(location.latitude))
            put("p_longitude", kotlinx.serialization.json.JsonPrimitive(location.longitude))
            if (accuracyMeters != null) put("p_accuracy_m", kotlinx.serialization.json.JsonPrimitive(accuracyMeters))
            put("p_recorded_at", kotlinx.serialization.json.JsonPrimitive(kotlinx.datetime.Instant.fromEpochMilliseconds(recordedAtMillis).toString()))
        }).decodeAs<String>()
        check(confirmed == routeId) { "O servidor não confirmou a posição da jornada." }
    }

    suspend fun updateLocationForUser(userId: String, latitude: Double, longitude: Double) {
        // The RPC resolves vendors.user_id from auth.uid() and rejects non-vendors.
        // Never accept a caller-supplied vendor identity or cache another user's profile.
        check(AuthRepository.currentUser()?.id == userId) { "Usuário inválido." }
        supabase.postgrest.rpc("update_vendor_location", kotlinx.serialization.json.buildJsonObject {
            put("p_latitude", kotlinx.serialization.json.JsonPrimitive(latitude))
            put("p_longitude", kotlinx.serialization.json.JsonPrimitive(longitude))
        })
    }

    suspend fun setOnline(isOnline: Boolean) {
        val user = AuthRepository.currentUser() ?: error("Entre na sua conta.")
        val vendorId = resolveRouteVendorId(user.id)
        val result = supabase.postgrest["vendors"].update({ set("is_online", isOnline) }) {
            filter { eq("id", vendorId) }
            select()
        }.decodeList<VendorIdentity>()
        check(result.singleOrNull()?.id == vendorId) { "Não foi possível alterar sua disponibilidade." }
    }

    // One serialized refresh loop: bounded rate even under a continuous stream of GPS events.
    // No process-wide replay/cache: private owner data cannot survive an account switch.
    fun listenToVendors(page: Int = 0, vendorId: String? = null, query: String = "", location: org.example.project.core.utils.Location? = null, category: String = ""): Flow<List<Vendor>> = flow {
        val hints = flow {
            val channel = supabase.channel("catalog-${kotlin.uuid.Uuid.random()}")
            try {
                val tables = listOf("vendors", "vendor_locations") + if (vendorId != null || query.isNotBlank() || category.isNotBlank()) listOf("products") else emptyList()
                val changes = tables.map { name ->
                    channel.postgresChangeFlow<PostgresAction>("public") { table = name }.map { Unit }
                }
                channel.subscribe()
                kotlinx.coroutines.flow.merge(*changes.toTypedArray()).collect { emit(Unit) }
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { supabase.realtime.removeChannel(channel) }
            }
        }
        refreshingCatalog(
            load = { if (vendorId != null) listOfNotNull(getById(vendorId)) else if (location != null) discover(location, page, query, category) else getVendors(page, query) },
            changes = hints
        ).collect { emit(it) }
    }

    fun listenToVendorStates(page: Int = 0, vendorId: String? = null, query: String = "", location: org.example.project.core.utils.Location? = null, category: String = ""): Flow<org.example.project.core.utils.UiState<List<Vendor>>> =
        listenToVendors(page, vendorId, query, location, category).map<List<Vendor>, org.example.project.core.utils.UiState<List<Vendor>>> {
            org.example.project.core.utils.UiState.Success(it)
        }.retryWhen { cause, attempt ->
            if (cause is kotlinx.coroutines.CancellationException || GeneratedAppConfiguration.supabaseKey.isBlank()) false
            else {
                emit(org.example.project.core.utils.UiState.Error("Conexão interrompida. Tentando atualizar os vendedores."))
                delay(minOf(30_000L, 1_000L * (1L shl attempt.coerceAtMost(5).toInt())))
                true
            }
        }.catch { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            emit(org.example.project.core.utils.UiState.Error("Não foi possível carregar os vendedores. Verifique a configuração e a conexão."))
        }

    suspend fun getById(id: String): Vendor? = supabase.postgrest["vendor_catalog"].select {
        filter { eq("id", id) }
    }.decodeSingleOrNull<Vendor>()

    suspend fun getByUserId(userId: String): Vendor? {
        check(AuthRepository.currentUser()?.id == userId) { "Usuário inválido." }
        return supabase.postgrest["vendor_catalog"].select(columns = summaryColumns) {
            filter { eq("user_id", userId) }
        }.decodeSingleOrNull<Vendor>()
    }

    suspend fun resolveRouteVendorId(userId: String): String = getByUserId(userId)?.id
        ?: error("Seu perfil profissional ainda não está disponível.")

    private suspend fun searchCatalog(query: String, category: String, page: Int): List<Vendor> {
        val bounds = pageBounds(page, PAGE_SIZE)
        require(query.length <= 120)
        return supabase.postgrest.rpc("search_vendor_catalog", kotlinx.serialization.json.buildJsonObject {
            put("p_query", kotlinx.serialization.json.JsonPrimitive(query.trim()))
            put("p_category", kotlinx.serialization.json.JsonPrimitive(category))
            put("p_offset", kotlinx.serialization.json.JsonPrimitive(bounds.first))
            put("p_limit", kotlinx.serialization.json.JsonPrimitive(PAGE_SIZE))
        }).decodeList<Vendor>()
    }

    suspend fun discover(location: org.example.project.core.utils.Location, page: Int = 0, query: String = "", category: String = ""): List<Vendor> {
        require(org.example.project.core.domain.route.RouteProgress.valid(location))
        val bounds = pageBounds(page, PAGE_SIZE)
        return supabase.postgrest.rpc("discover_vendors", kotlinx.serialization.json.buildJsonObject {
            put("p_latitude", kotlinx.serialization.json.JsonPrimitive(location.latitude))
            put("p_longitude", kotlinx.serialization.json.JsonPrimitive(location.longitude))
            put("p_query", kotlinx.serialization.json.JsonPrimitive(query.take(120)))
            put("p_category", kotlinx.serialization.json.JsonPrimitive(category))
            put("p_offset", kotlinx.serialization.json.JsonPrimitive(bounds.first))
            put("p_limit", kotlinx.serialization.json.JsonPrimitive(PAGE_SIZE))
        }).decodeList<Vendor>()
    }

    suspend fun getByCategory(category: String, page: Int = 0): List<Vendor> = searchCatalog("", category, page)

    // These screens have no persistence yet; avoid cross-user demo IDs/messages.
    val favorites = emptyList<String>()
    val notifications = emptyList<Pair<String, String>>()
}

@Serializable
internal data class VendorIdentity(val id: String)
