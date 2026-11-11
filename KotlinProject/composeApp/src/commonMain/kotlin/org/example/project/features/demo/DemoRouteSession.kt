package org.example.project.features.demo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.project.core.data.RouteDataSource
import org.example.project.core.data.RouteRepository
import org.example.project.core.domain.models.PurchaseIntent
import org.example.project.core.domain.models.RouteWaypoint
import org.example.project.core.domain.models.VendorRoute
import org.example.project.core.utils.Location
import org.example.project.features.vendor.err.RouteDraftState

/** Local transport for the EXISTING route repository, not a GPS/backend implementation.
 * Only session memory. No fabricated coverage polygon: buffer calculation is unavailable. */
class DemoRouteSession(private val storage: DemoRouteStorage? = null) {
    private val saved = linkedMapOf<String, VendorRoute>()
    private val source = object : RouteDataSource {
        override suspend fun buffer(waypoints: List<RouteWaypoint>, toleranceMeters: Int): List<Location> =
            error("A simulação não calcula área de cobertura.")
        override suspend fun insert(route: VendorRoute): String {
            require(route.vendorId == "demo-route-owner" && !route.isActive)
            require(route.waypoints.size in 2..2000)
            val next = (saved.keys.mapNotNull { it.removePrefix("demo-route-").toIntOrNull() }.maxOrNull() ?: 0) + 1
            val id = "demo-route-$next"
            val stored = route.copy(id = id)
            val nextRoutes = saved.values.toList() + stored
            // Persist first: an IO failure must not create a false in-memory success.
            storage?.write(encodeDemoRoutes(nextRoutes))
            saved[id] = stored
            records.value = saved.values.toList()
            return id
        }
        override suspend fun active(vendorId: String): VendorRoute? = null
        override suspend fun saved(vendorId: String): List<VendorRoute> = saved.values.filter { it.vendorId == vendorId }
        override suspend fun load(routeId: String): VendorRoute? = saved[routeId]
        override suspend fun intents(routeId: String): List<PurchaseIntent> = emptyList()
        override suspend fun end(routeId: String) { error("Nenhuma rota real foi iniciada.") }
    }
    private val repository = RouteRepository(source)
    private val state = MutableStateFlow(RouteDraftState())
    val draft = state.asStateFlow()
    private val records = MutableStateFlow<List<VendorRoute>>(emptyList())
    val routes = records.asStateFlow()
    val syntheticPoints = listOf(
        Location(-23.5498, -46.6342), Location(-23.5498, -46.6338),
        Location(-23.5498, -46.6333), Location(-23.5502, -46.6333),
        Location(-23.5507, -46.6333), Location(-23.5511, -46.6333)
    )
    suspend fun restoreSavedRoutes() {
        val stored = storage?.read()?.let(::decodeDemoRoutes) ?: return
        saved.clear(); stored.forEach { saved[it.id] = it }; records.value = stored
    }
    suspend fun savePreparedRoute(name: String, waypoints: List<RouteWaypoint>): String =
        repository.saveRoute(VendorRoute("", "demo-route-owner", name, waypoints, emptyList()))

    suspend fun automaticAttendance(id: String): org.example.project.core.domain.route.RouteAttendance {
        val route = repository.getRoute(id) ?: error("Rota não pertence a este armazenamento demo.")
        return org.example.project.features.vendor.err.ERREngine(repository).automaticAttendance(route)
    }
    fun startRecording() { state.value = state.value.startRecording(null) }
    fun sampleNext() {
        if (!state.value.isRecording) return
        syntheticPoints.getOrNull(state.value.waypoints.size)?.let { state.value = state.value.recordLocation(it) }
    }
    fun finishRecording() { state.value = state.value.finishRecording() }
    suspend fun save(): String {
        check(!state.value.isRecording && state.value.canStartRoute) { "Encerre o percurso com pelo menos dois pontos." }
        val id = repository.saveRoute(VendorRoute("", "demo-route-owner", "Rota da manhã (demo)", state.value.waypoints, emptyList()))
        records.value = saved.values.toList()
        return id
    }
    suspend fun reuse(id: String): VendorRoute {
        val route = repository.getRoute(id) ?: error("Rota não pertence a esta sessão demo.")
        state.value = RouteDraftState(waypoints = route.waypoints)
        return route
    }
}
