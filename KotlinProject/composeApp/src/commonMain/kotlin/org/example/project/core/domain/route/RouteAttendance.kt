package org.example.project.core.domain.route

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.example.project.core.domain.models.PurchaseIntent
import org.example.project.core.domain.models.VendorRoute
import org.example.project.core.utils.Location
import kotlin.math.max

/** Derived states ONLY. Never serialize these values to purchase_intents.status. */
enum class AttendanceStatus(val label: String) {
    Sent("Interesse enviado ao vendedor"), InVendorList("Interesse na lista do vendedor"),
    Behind("Ponto já ultrapassado nesta rota"), OutsideRoute("Vendedor fora de alcance do ponto"),
    AmbiguousPoint("Ponto ambíguo no percurso; confirme outro ponto"),
    InvalidPoint("Ponto inválido; confirme a localização"),
    Paused("Jornada pausada; aguardando retomada"), NoConnection("Sem conexão; aguardando atualização"),
    NoGps("Localização do vendedor indisponível"), Cancelled("Solicitação cancelada"),
    JourneyEnded("Jornada encerrada"), Unavailable("Interesse não disponível na origem"),
    Duplicate("Interesse já associado a outro protocolo")
}
enum class JourneyPhase { Ready, Working, Paused, Ended }
enum class GpsIssue { Missing, Stale, LowAccuracy, OffRoute, Ambiguous, ImplausibleJump, Backtracking }

/** revision is a monotonic sequence from ONE authoritative feed, including tombstones.
 * Full-snapshot adapters may instead use a serialized LOCAL sequence, with no interleaved deltas. */
data class RouteInterestUpdate(val vendorId: String, val intent: PurchaseIntent, val revision: Long, val cancelled: Boolean = false, val cancelConsumerThroughRevision: Boolean = true)
data class RouteGpsFix(val location: Location, val accuracyMeters: Double, val recordedAtMillis: Long)
data class AttendanceEntry(val intent: PurchaseIntent, val status: AttendanceStatus, val alongMeters: Double?, val crossTrackMeters: Double?)
data class AttendanceSnapshot(
    val phase: JourneyPhase = JourneyPhase.Ready, val connected: Boolean = true,
    val progressMeters: Double = 0.0, val location: Location? = null, val gpsIssue: GpsIssue? = GpsIssue.Missing,
    val entries: List<AttendanceEntry> = emptyList(), val queue: List<AttendanceEntry> = emptyList()
)

/** Single serialized reducer: events, GPS, lifecycle and reconciliation never race.
 * Projects an interest only when its coordinates change, not on every GPS sample.
 * Cancelled/behind/out-of-range records stay visible; automatic receipt is never a sale. */
class RouteAttendance(val route: VendorRoute) {
    val geometry = RouteProgress(route.waypoints)
    private data class Record(val update: RouteInterestUpdate, val projection: RouteProgress.Projection?, val present: Boolean = true)
    private val mutex = Mutex()
    private val records = linkedMapOf<String, Record>()
    private val cancelledIds = mutableSetOf<String>()
    private val cancelledThrough = mutableMapOf<String, Long>()
    private var snapshotWatermark = -1L
    private var phase = JourneyPhase.Ready
    private var connected = true
    private var progress = 0.0
    private var lastFix: RouteGpsFix? = null
    private var gpsIssue: GpsIssue? = GpsIssue.Missing
    private var location: Location? = null
    private val mutableState = MutableStateFlow(AttendanceSnapshot())
    val state = mutableState.asStateFlow()

    suspend fun start() = mutex.withLock {
        check(phase != JourneyPhase.Ended) { "Inicie outra jornada para reutilizar a rota encerrada." }
        if (phase == JourneyPhase.Ready) phase = JourneyPhase.Working
        publish()
    }
    suspend fun pause() = mutex.withLock { if (phase == JourneyPhase.Working) phase = JourneyPhase.Paused; publish() }
    suspend fun resume() = mutex.withLock { if (phase == JourneyPhase.Paused) phase = JourneyPhase.Working; publish() }
    suspend fun end() = mutex.withLock { phase = JourneyPhase.Ended; publish() }
    suspend fun connection(available: Boolean) = mutex.withLock { connected = available; publish() }
    suspend fun gpsUnavailable() = mutex.withLock { gpsIssue = GpsIssue.Missing; publish() }
    suspend fun tick(nowMillis: Long) = mutex.withLock {
        require(nowMillis >= 0)
        if (lastFix == null || nowMillis - lastFix!!.recordedAtMillis > MAX_FIX_AGE_MS) gpsIssue = GpsIssue.Stale
        publish()
    }
    suspend fun gps(fix: RouteGpsFix, nowMillis: Long) = mutex.withLock {
        require(nowMillis >= 0)
        if (phase != JourneyPhase.Working) return@withLock
        val previous = lastFix
        if (previous != null && fix.recordedAtMillis <= previous.recordedAtMillis) {
            if (nowMillis - previous.recordedAtMillis > MAX_FIX_AGE_MS) { gpsIssue = GpsIssue.Stale; publish() }
            return@withLock
        }
        gpsIssue = when {
            fix.recordedAtMillis < 0 || fix.recordedAtMillis > nowMillis || nowMillis - fix.recordedAtMillis > MAX_FIX_AGE_MS -> GpsIssue.Stale
            !RouteProgress.valid(fix.location) || !fix.accuracyMeters.isFinite() || fix.accuracyMeters !in 0.0..50.0 -> GpsIssue.LowAccuracy
            else -> null
        }
        if (gpsIssue != null) { publish(); return@withLock }
        val projected = geometry.project(fix.location)!!
        gpsIssue = when {
            projected.ambiguous -> GpsIssue.Ambiguous
            projected.crossTrackMeters > route.deviationToleranceMeters -> GpsIssue.OffRoute
            projected.alongMeters < progress - 30.0 -> GpsIssue.Backtracking
            previous != null && projected.alongMeters - progress >
                15.0 * (fix.recordedAtMillis - previous.recordedAtMillis) / 1000.0 + 2.0 * fix.accuracyMeters -> GpsIssue.ImplausibleJump
            else -> null
        }
        if (gpsIssue == null) {
            progress = max(progress, projected.alongMeters)
            lastFix = fix
            location = fix.location
        }
        publish()
    }
    suspend fun upsert(updates: List<RouteInterestUpdate>) = mutex.withLock {
        validate(updates)
        updates.sortedWith(compareBy<RouteInterestUpdate> { it.revision }.thenBy { it.intent.id }).forEach { apply(it) }
        publish()
    }
    /** Full, authoritative snapshot INCLUDING cancellations. Missing is explicit, never inferred cancellation.
     * Newer concurrent deltas survive a snapshot watermark; older snapshots cannot resurrect rows. */
    suspend fun reconcile(updates: List<RouteInterestUpdate>, watermark: Long) = mutex.withLock {
        require(watermark >= 0 && updates.all { it.revision <= watermark })
        if (watermark <= snapshotWatermark) return@withLock
        validate(updates)
        updates.sortedWith(compareBy<RouteInterestUpdate> { it.revision }.thenBy { it.intent.id }).forEach { apply(it, fromSnapshot = true) }
        val ids = updates.map { it.intent.id }.toSet()
        records.replaceAllCompat { id, record ->
            if (id !in ids && record.update.revision <= watermark) record.copy(present = false) else record
        }
        snapshotWatermark = watermark
        publish()
    }
    private fun validate(updates: List<RouteInterestUpdate>) {
        require((records.keys + updates.map { it.intent.id }).distinct().size <= 2000) { "Limite local de interesses atingido." }
        updates.forEach {
            require(it.vendorId == route.vendorId && it.revision >= 0 && it.intent.id.isNotBlank() && it.intent.consumerId.isNotBlank()) { "Evento não pertence à jornada." }
            require(records[it.intent.id]?.update?.intent?.consumerId?.let { owner -> owner == it.intent.consumerId } != false) { "Identidade do interesse não pode mudar." }
        }
        require(updates.groupBy { it.intent.id }.values.all { values -> values.map { it.intent.consumerId }.distinct().size == 1 })
    }
    private fun apply(update: RouteInterestUpdate, fromSnapshot: Boolean = false) {
        val id = update.intent.id
        val old = records[id]
        if (!fromSnapshot && update.revision <= snapshotWatermark) return
        if (old != null && update.revision < old.update.revision) return
        if (old != null && update.revision == old.update.revision) {
            if (fromSnapshot && old.update == update) records[id] = old.copy(present = true)
            return // conflicting replay cannot mutate the payload
        }
        if (update.cancelled) {
            cancelledIds += id
            if (update.cancelConsumerThroughRevision) cancelledThrough[update.intent.consumerId] = max(cancelledThrough[update.intent.consumerId] ?: -1, update.revision)
        }
        val projection = if (old != null && old.update.intent.location == update.intent.location) old.projection else geometry.project(update.intent.location)
        records[id] = Record(update, projection)
    }
    private fun publish() {
        val canonical = records.values.filter { it.present && it.update.intent.status == "pending" &&
            it.update.intent.id !in cancelledIds && it.update.revision > (cancelledThrough[it.update.intent.consumerId] ?: -1) }
            .groupBy { it.update.intent.consumerId }.mapValues { (_, group) -> group.minOf { it.update.intent.id } }
        val entries = records.values.map { record ->
            val u = record.update; val p = record.projection
            val status = when {
                u.cancelled || u.intent.id in cancelledIds || u.revision <= (cancelledThrough[u.intent.consumerId] ?: -1) -> AttendanceStatus.Cancelled
                phase == JourneyPhase.Ended -> AttendanceStatus.JourneyEnded
                !record.present || u.intent.status != "pending" -> AttendanceStatus.Unavailable
                canonical[u.intent.consumerId] != u.intent.id -> AttendanceStatus.Duplicate
                phase == JourneyPhase.Ready -> AttendanceStatus.Sent
                phase == JourneyPhase.Paused -> AttendanceStatus.Paused
                !connected -> AttendanceStatus.NoConnection
                gpsIssue != null -> AttendanceStatus.NoGps
                p == null -> AttendanceStatus.InvalidPoint
                p.ambiguous -> AttendanceStatus.AmbiguousPoint
                p.crossTrackMeters > route.deviationToleranceMeters -> AttendanceStatus.OutsideRoute
                p.alongMeters < progress -> AttendanceStatus.Behind
                else -> AttendanceStatus.InVendorList
            }
            AttendanceEntry(u.intent, status, p?.alongMeters, p?.crossTrackMeters)
        }.sortedBy { it.intent.id }
        val queue = entries.filter { it.status == AttendanceStatus.InVendorList }.sortedWith(
            compareBy<AttendanceEntry> { it.alongMeters }.thenBy { it.intent.id })
        mutableState.value = AttendanceSnapshot(phase, connected, progress, location, gpsIssue, entries, queue)
    }
    private inline fun <K, V> MutableMap<K, V>.replaceAllCompat(transform: (K, V) -> V) {
        keys.toList().forEach { key -> this[key] = transform(key, getValue(key)) }
    }
    private companion object { const val MAX_FIX_AGE_MS = 30_000L }
}
