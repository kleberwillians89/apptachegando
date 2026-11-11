package org.example.project.core.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** Latest position wins. One request in flight, no offline backlog, at most one per 10s.
 * The 120s server TTL is intentionally unchanged; no last-known position is replayed on restart.
 * The caller handles/report errors; cancellation is owned by the screen.
 */
internal data class LocationPublication(val location: Location, val recordedAtMillis: Long, val accuracyMeters: Double?)

internal class LocationPublisher(
    scope: CoroutineScope,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val publish: suspend (LocationPublication) -> Unit
) {
    private val pending = Channel<LocationPublication>(Channel.CONFLATED)
    private val worker = scope.launch {
        for (sample in pending) {
            // A slow previous request must not turn an old queued position into a fresh server fix.
            if (nowMillis() - sample.recordedAtMillis in 0L..30_000L) {
                publish(sample)
                delay(10_000)
            }
        }
    }
    fun offer(location: Location, recordedAtMillis: Long = nowMillis(), accuracyMeters: Double? = null) { pending.trySend(LocationPublication(location, recordedAtMillis, accuracyMeters)) }
    fun close() { pending.close(); worker.cancel() }
}
