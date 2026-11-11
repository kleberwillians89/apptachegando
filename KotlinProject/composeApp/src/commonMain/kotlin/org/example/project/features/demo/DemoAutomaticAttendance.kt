package org.example.project.features.demo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.example.project.core.domain.models.PurchaseIntent
import org.example.project.core.domain.models.RouteWaypoint
import org.example.project.core.domain.route.*
import org.example.project.core.utils.Location

/** Controlled feed over the existing ERR/route repository. No Supabase/GPS/notifications.
 * Only route geometry can be persisted by DemoRouteSession; interests remain ephemeral. */
data class DemoAttendanceContext(val vendorName: String = "Vendedor fictício", val category: String = "Pães")

class DemoAutomaticAttendance(val routes: DemoRouteSession) {
    private val context = MutableStateFlow(DemoAttendanceContext())
    val consumerContext = context.asStateFlow()
    fun describeConsumerContext(vendor: org.example.project.core.data.Vendor, product: org.example.project.core.data.Product) {
        require(vendor.id.startsWith("demo-vendor-") && vendor.products.any { it.id == product.id && it.name == product.name })
        require(product.name in demoCategories)
        context.value = DemoAttendanceContext(vendor.name, product.name)
    }
    private val selected = MutableStateFlow<RouteAttendance?>(null)
    val attendance = selected.asStateFlow()
    private val mutex = Mutex()
    private val wire = linkedMapOf<String, RouteInterestUpdate>()
    private var revision = 0L
    private var now = 1_000_000L // logical time, explicitly synthetic
    private var lastBatch = emptyList<RouteInterestUpdate>()
    private var clientCounter = 0
    private val mine = MutableStateFlow<String?>(null)
    val consumerProtocol = mine.asStateFlow()
    private val cancelPending = MutableStateFlow(false)
    val consumerCancellationPending = cancelPending.asStateFlow()
    private val completed = MutableStateFlow<List<AttendanceSnapshot>>(emptyList())
    val previousJourneys = completed.asStateFlow()
    suspend fun chooseRouteAgain() = mutex.withLock {
        val e = engine(); check(e.state.value.phase == JourneyPhase.Ended)
        require(completed.value.size < 20) { "Limite de histórico desta sessão atingido." }
        completed.value = completed.value + e.state.value
        selected.value = null
    }
    private fun engine() = selected.value ?: error("Selecione uma rota demo salva.")

    suspend fun prepareRoute(): String = routes.savePreparedRoute("Centro de São Paulo · fictícia", fixtureWaypoints)
    suspend fun select(id: String) = mutex.withLock {
        check(selected.value?.state?.value?.phase !in listOf(JourneyPhase.Working, JourneyPhase.Paused)) { "Encerre a jornada antes de trocar a rota." }
        selected.value = routes.automaticAttendance(id)
        wire.clear(); revision = 0; now = 1_000_000; lastBatch = emptyList(); mine.value = null; cancelPending.value = false; clientCounter = 0
    }
    suspend fun start() = withContext(Dispatchers.Default) {
        val e = engine(); e.start(); e.gps(RouteGpsFix(e.geometry.locationAt(e.state.value.progressMeters), 5.0, now), now)
    }
    suspend fun pause() { engine().pause() }
    suspend fun resume() { engine().resume(); restoreGps() }
    suspend fun end() { engine().end() }
    suspend fun advance() = withContext(Dispatchers.Default) {
        mutex.withLock {
            val e = engine(); check(e.state.value.phase == JourneyPhase.Working)
            now += 30_000
            e.gps(RouteGpsFix(e.geometry.locationAt(e.state.value.progressMeters + e.geometry.lengthMeters * 0.30), 5.0, now), now)
        }
    }
    suspend fun loseGps() { engine().gpsUnavailable() }
    suspend fun restoreGps() = mutex.withLock {
        val e = engine(); now += 10_000
        e.gps(RouteGpsFix(e.geometry.locationAt(e.state.value.progressMeters), 5.0, now), now)
    }
    suspend fun toggleConnection() = mutex.withLock {
        val e = engine()
        if (e.state.value.connected) e.connection(false) else {
            // Full snapshot before declaring the local simulated connection ready.
            e.reconcile(wire.values.toList(), revision)
            e.connection(true)
            cancelPending.value = false
        }
    }
    suspend fun simulateClients(count: Int = 10) = withContext(Dispatchers.Default) {
        require(count in 1..450)
        mutex.withLock {
            val e = engine(); check(e.state.value.phase == JourneyPhase.Working)
            require(wire.size + count <= 2000) { "Limite de interesses desta sessão atingido." }
            val batch = (0 until count).map { index ->
                val number = ++clientCounter
                val fraction = if (count == 1) 0.65 else 0.08 + 0.84 * index / (count - 1)
                RouteInterestUpdate(e.route.vendorId, PurchaseIntent("demo-auto-$number", "demo-client-$number",
                    e.geometry.locationAt(e.geometry.lengthMeters * fraction)), ++revision)
            }
            batch.forEach { wire[it.intent.id] = it }; lastBatch = batch
            if (e.state.value.connected) e.upsert(batch)
        }
    }
    suspend fun replayEvents() = withContext(Dispatchers.Default) { if (engine().state.value.connected) engine().upsert(lastBatch) }
    fun meetingPoint(option: Int): Location {
        val e = engine()
        return when (option) {
            0 -> e.geometry.locationAt(e.state.value.progressMeters + e.geometry.lengthMeters * 0.25)
            1 -> if (e.state.value.progressMeters < 150.0) e.geometry.locationAt(0.0).let { it.copy(longitude = it.longitude - 0.004) }
                else e.geometry.locationAt(e.state.value.progressMeters - 150.0)
            else -> e.geometry.locationAt(e.state.value.progressMeters).let { it.copy(latitude = it.latitude + 0.01) }
        }
    }
    suspend fun consumerInterest(point: Location): String = mutex.withLock {
        val e = engine()
        check(e.state.value.phase in listOf(JourneyPhase.Working, JourneyPhase.Paused)) { "Jornada não está disponível." }
        mine.value?.let { id ->
            val current = wire[id]
            if (current != null && !current.cancelled) {
                if (current.intent.location != point) {
                    val moved = current.copy(intent = current.intent.copy(location = point), revision = ++revision)
                    wire[id] = moved
                    if (e.state.value.connected) e.upsert(listOf(moved))
                }
                return@withLock id
            }
        }
        require(wire.size < 2000)
        val id = "demo-auto-consumer-${++revision}"
        val event = RouteInterestUpdate(e.route.vendorId, PurchaseIntent(id, "demo-consumer-view", point), revision)
        wire[id] = event; mine.value = id; cancelPending.value = false
        if (e.state.value.connected) e.upsert(listOf(event))
        id
    }
    suspend fun cancelConsumerInterest() = mutex.withLock {
        val id = mine.value ?: return@withLock
        val event = wire.getValue(id).copy(revision = ++revision, cancelled = true)
        wire[id] = event
        cancelPending.value = !engine().state.value.connected
        if (engine().state.value.connected) engine().upsert(listOf(event))
    }
    companion object {
        val fixtureWaypoints = listOf(
            Location(-23.5510, -46.6360), Location(-23.5510, -46.6338), Location(-23.5510, -46.6316),
            Location(-23.5485, -46.6316), Location(-23.5485, -46.6338), Location(-23.5485, -46.6360)
        ).mapIndexed { index, p -> RouteWaypoint(p.latitude, p.longitude, index) }
    }
}
