package org.example.project.core.data

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.example.project.core.domain.models.*
import org.example.project.core.utils.Location

/** Injectable transport: a failed insert must never become an in-memory success. */
interface RouteDataSource {
    suspend fun buffer(waypoints: List<RouteWaypoint>, toleranceMeters: Int): List<Location>
    suspend fun insert(route: VendorRoute): String
    suspend fun active(vendorId: String): VendorRoute?
    suspend fun saved(vendorId: String): List<VendorRoute>
    suspend fun load(routeId: String): VendorRoute?
    suspend fun intents(routeId: String): List<PurchaseIntent>
    suspend fun end(routeId: String)
}

class RouteRepository(private val source: RouteDataSource = SupabaseRouteDataSource()) {
    suspend fun generateBufferPolygon(waypoints: List<RouteWaypoint>, toleranceMeters: Int): List<Location> {
        validateRouteWaypoints(waypoints)
        require(toleranceMeters in 50..300)
        return source.buffer(waypoints, toleranceMeters).also {
            check(it.size >= 4) { "Não foi possível calcular a área da rota." }
        }
    }
    suspend fun saveRoute(route: VendorRoute): String {
        validateRouteWaypoints(route.waypoints)
        require(route.vendorId.isNotBlank()) { "Entre como profissional para salvar a rota." }
        require(route.name.trim().length in 1..160) { "Use um nome de rota com até 160 caracteres." }
        require(route.activeDays.isNotEmpty()) { "Escolha pelo menos um dia." }
        require(route.deviationToleranceMeters in 50..300)
        return source.insert(route).also {
            check(it.isNotBlank()) { "O servidor não confirmou o salvamento da rota." }
        }
    }
    suspend fun getActiveRoute(vendorId: String): VendorRoute? = source.active(vendorId)
    suspend fun getSavedRoutes(vendorId: String): List<VendorRoute> = source.saved(vendorId)
    suspend fun getRoute(routeId: String): VendorRoute? = source.load(routeId)
    suspend fun fetchIntentsForRoute(routeId: String): List<PurchaseIntent> = source.intents(routeId)
    suspend fun endRoute(routeId: String) = source.end(routeId)
}

internal fun validateRouteWaypoints(points: List<RouteWaypoint>) {
    require(points.size in 2..2000) { "A rota precisa ter de 2 a 2000 pontos." }
    require(points.all { it.latitude.isFinite() && it.latitude in -90.0..90.0 &&
        it.longitude.isFinite() && it.longitude in -180.0..180.0 }) { "A rota contém uma posição inválida." }
    require(points.map { it.latitude to it.longitude }.distinct().size >= 2) { "Grave dois pontos diferentes." }
    require(points.map { it.orderIndex }.sorted() == points.indices.toList()) { "A sequência do percurso é inválida." }
}

@Serializable
internal data class RouteRecord(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    val name: String,
    val waypoints: List<RouteWaypoint>,
    @SerialName("active_days") val activeDays: List<String>,
    @SerialName("deviation_tolerance_meters") val tolerance: Int,
    val status: String,
    val polygon: JsonArray
) {
    fun toDomain() = VendorRoute(id, vendorId, name, waypoints, polygonLocations(polygon), tolerance,
        activeDays.map { value -> RouteWeekday.entries.first { it.storageValue == value } }, status == "active")
}

internal fun polygonLocations(rings: JsonArray): List<Location> = rings.first().jsonArray.map {
    val pair = it.jsonArray
    Location(pair[1].jsonPrimitive.double, pair[0].jsonPrimitive.double)
}

private class SupabaseRouteDataSource : RouteDataSource {
    @Serializable private data class SavedId(val id: String)
    @Serializable private data class RouteInsert(
        @SerialName("vendor_id") val vendorId: String,
        val name: String,
        val waypoints: List<RouteWaypoint>,
        @SerialName("active_days") val activeDays: List<String>,
        @SerialName("deviation_tolerance_meters") val tolerance: Int,
        val status: String
    )
    @Serializable private data class IntentRow(
        val id: String, @SerialName("consumer_id") val consumerId: String,
        val lat: Double, val lng: Double, val status: String
    )
    override suspend fun buffer(waypoints: List<RouteWaypoint>, toleranceMeters: Int): List<Location> {
        val params = buildJsonObject {
            put("points", buildJsonArray { waypoints.sortedBy { it.orderIndex }.forEach { p ->
                add(buildJsonArray { add(p.longitude); add(p.latitude) })
            } })
            put("tolerance_meters", toleranceMeters)
        }
        return polygonLocations(Json.parseToJsonElement(supabase.postgrest.rpc("create_route_buffer", params).data).jsonArray)
    }
    override suspend fun insert(route: VendorRoute): String = supabase.postgrest["vendor_routes"].insert(
        RouteInsert(route.vendorId, route.name, route.waypoints.sortedBy { it.orderIndex },
            route.activeDays.map { it.storageValue }, route.deviationToleranceMeters,
            if (route.isActive) "active" else "draft")
    ) { select() }.decodeSingle<SavedId>().id

    override suspend fun active(vendorId: String): VendorRoute? = supabase.postgrest["route_details"].select {
        filter { eq("vendor_id", vendorId); eq("status", "active") }
    }.decodeSingleOrNull<RouteRecord>()?.toDomain()

    override suspend fun saved(vendorId: String): List<VendorRoute> = supabase.postgrest["route_details"].select {
        filter { eq("vendor_id", vendorId); neq("status", "active") }
        order("name", Order.ASCENDING)
        order("id", Order.ASCENDING)
        limit(20)
    }.decodeList<RouteRecord>().map { it.toDomain() }

    override suspend fun load(routeId: String): VendorRoute? = supabase.postgrest["route_details"].select {
        filter { eq("id", routeId) }
    }.decodeSingleOrNull<RouteRecord>()?.toDomain()

    override suspend fun intents(routeId: String): List<PurchaseIntent> = supabase.postgrest.rpc(
        "get_intents_for_route", buildJsonObject { put("p_route_id", routeId) }
    ).decodeList<IntentRow>().map { PurchaseIntent(it.id, it.consumerId, Location(it.lat, it.lng), it.status) }

    override suspend fun end(routeId: String) {
        RouteAttendanceRepository().setAvailability(routeId, online = false, end = true)
    }
}
