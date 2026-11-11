package org.example.project.core.data

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.example.project.core.domain.models.PurchaseIntent
import org.example.project.core.domain.route.*
import org.example.project.core.utils.Location
import org.example.project.core.utils.UiState

@Serializable
data class RemoteAttendanceSnapshot(
    @SerialName("route_id") val routeId: String,
    @SerialName("vendor_id") val vendorId: String,
    val active: Boolean, val online: Boolean, val intents: List<RemoteAttendanceIntent>,
    @kotlinx.serialization.Transient val requestSequence: Long = 0
)
@Serializable
data class RemoteAttendanceIntent(
    val id: String, @SerialName("consumer_id") val consumerId: String,
    val lat: Double, val lng: Double, val status: String
)

/** One feed, full snapshots only. Sequence is LOCAL request order, never a server revision.
 * No Realtime payload/delta enters the reducer. Failed reads preserve the previous records. */
class RemoteAttendanceSession(val attendance: RouteAttendance) {
    private var sequence = 0L
    private val mutex = Mutex()
    suspend fun apply(snapshot: RemoteAttendanceSnapshot) = mutex.withLock {
        require(snapshot.routeId == attendance.route.id && snapshot.vendorId == attendance.route.vendorId)
        require(snapshot.intents.size <= 2000 && snapshot.intents.map { it.id }.distinct().size == snapshot.intents.size)
        if (snapshot.requestSequence > 0 && snapshot.requestSequence <= sequence) return@withLock
        val revision = if (snapshot.requestSequence > 0) snapshot.requestSequence else sequence + 1
        val updates = snapshot.intents.map {
            RouteInterestUpdate(snapshot.vendorId, PurchaseIntent(it.id, it.consumerId, Location(it.lat, it.lng), it.status), revision, it.status == "cancelled", cancelConsumerThroughRevision = false)
        }
        // Validate/reconcile before changing lifecycle or clearing the connection error.
        attendance.reconcile(updates, revision)
        sequence = revision
        if (!snapshot.active) attendance.end()
        else {
            attendance.start()
            if (snapshot.online) attendance.resume() else { attendance.gpsUnavailable(); attendance.pause() }
        }
        attendance.connection(true)
    }
    /** Called only after the server confirms the command's route UUID.
     * Blocks older completed reads and stops GPS through the confirmed lifecycle immediately.
     * A later failed refresh cannot undo a confirmed pause or claim a current queue. */
    suspend fun confirmAvailability(requestSequence: Long, online: Boolean, end: Boolean = false) = mutex.withLock {
        require(requestSequence > 0 && !(end && online))
        if (requestSequence <= sequence) return@withLock
        sequence = requestSequence
        attendance.gpsUnavailable()
        attendance.connection(false)
        if (end) attendance.end()
        else {
            attendance.start()
            if (online) attendance.resume() else attendance.pause()
        }
    }

    suspend fun failed() = attendance.connection(false)
}

interface AttendanceTransport {
    suspend fun snapshot(routeId: String): RemoteAttendanceSnapshot
    suspend fun availability(routeId: String, online: Boolean, end: Boolean)
}

class RouteAttendanceRepository(private val transport: AttendanceTransport = SupabaseAttendanceTransport()) {
    private val requests = Mutex()
    private var sequence = 0L
    suspend fun refresh(routeId: String) = requests.withLock {
        transport.snapshot(routeId).copy(requestSequence = ++sequence)
    }
    suspend fun setAvailability(routeId: String, online: Boolean, end: Boolean = false) = requests.withLock {
        transport.availability(routeId, online, end)
        ++sequence
    }

    /** Polling remains authoritative if replication is not enabled or events are suppressed by RLS. */
    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    fun observe(routeId: String, vendorId: String): Flow<UiState<RemoteAttendanceSnapshot>> = channelFlow {
        val owner = AuthRepository.currentUser()?.id ?: error("Entre na sua conta.")
        val invalidations = Channel<Unit>(Channel.CONFLATED)
        val realtimeJob = launch {
            val channel = try { supabase.channel("attendance-${kotlin.uuid.Uuid.random()}") }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { return@launch }
            try {
                val changes = listOf("purchase_intents", "vendor_routes", "vendors").map { name ->
                    channel.postgresChangeFlow<PostgresAction>("public") {
                        table = name
                        filter(if (name == "purchase_intents") "vendor_id" else "id", FilterOperator.EQ,
                            if (name == "vendor_routes") routeId else vendorId)
                    }.map { Unit }
                }
                channel.subscribe()
                merge(*changes.toTypedArray()).collect { invalidations.trySend(Unit) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Polling below continues. No event payload is trusted. */ }
            finally { withContext(NonCancellable) { runCatching { supabase.realtime.removeChannel(channel) } } }
        }
        try {
            while (currentCoroutineContext().isActive) {
                if (AuthRepository.currentUser()?.id != owner) {
                    send(UiState.Error("A sessão mudou. Volte e entre novamente.")); break
                }
                send(backendResult("Não foi possível atualizar a fila. Verifique a conexão e a liberação do contrato de atendimento no servidor.") {
                    refresh(routeId).also { check(it.vendorId == vendorId && it.routeId == routeId) }
                })
                withTimeoutOrNull(10_000) { invalidations.receive() }
                delay(500) // Coalesce bursts; never overlap snapshot reads.
            }
        } finally { realtimeJob.cancelAndJoin(); invalidations.close() }
    }
}

private class SupabaseAttendanceTransport : AttendanceTransport {
    override suspend fun snapshot(routeId: String): RemoteAttendanceSnapshot = supabase.postgrest.rpc(
        "get_route_attendance", buildJsonObject { put("p_route_id", routeId) }
    ).decodeAs<RemoteAttendanceSnapshot>()
    override suspend fun availability(routeId: String, online: Boolean, end: Boolean) {
        val id = supabase.postgrest.rpc("set_route_availability", buildJsonObject {
            put("p_route_id", routeId); put("p_online", online); put("p_end", end)
        }).decodeAs<String>()
        check(id == routeId)
    }
}
