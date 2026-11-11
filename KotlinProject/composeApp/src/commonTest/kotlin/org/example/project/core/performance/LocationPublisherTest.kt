package org.example.project.core.performance

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.example.project.core.utils.Location
import org.example.project.core.utils.LocationPublisher

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocationPublisherTest {
    @Test fun publicationKeepsOriginalAccuracyAndCaptureTime() = runTest {
        val sent = mutableListOf<org.example.project.core.utils.LocationPublication>()
        val publisher = LocationPublisher(backgroundScope, nowMillis = { 10_000L }) { sent += it }
        publisher.offer(Location(-23.0, -46.0), 9_000, 7.5); runCurrent()
        assertEquals(9_000L, sent.single().recordedAtMillis)
        assertEquals(7.5, sent.single().accuracyMeters)
        publisher.close()
    }

    @Test fun slowRequestDoesNotPublishAnExpiredQueuedFixAsRecent() = runTest {
        val gate = CompletableDeferred<Unit>()
        val sent = mutableListOf<Location>()
        val publisher = LocationPublisher(backgroundScope, nowMillis = { testScheduler.currentTime }) {
            sent += it.location
            if (sent.size == 1) gate.await()
        }
        publisher.offer(Location(-23.0, -46.0)); runCurrent()
        publisher.offer(Location(-23.001, -46.001)); runCurrent()
        advanceTimeBy(40_000); gate.complete(Unit); runCurrent()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(1, sent.size)
        publisher.offer(Location(-23.002, -46.002)); runCurrent()
        assertEquals(2, sent.size)
        publisher.close()
    }

    @Test fun slowNetworkHasOneRequestAndKeepsOnlyNewestPosition() = runTest {
        val gate = CompletableDeferred<Unit>()
        val sent = mutableListOf<Location>()
        val publisher = LocationPublisher(backgroundScope) {
            sent += it.location
            if (sent.size == 1) gate.await()
        }
        publisher.offer(Location(1.0, 1.0)); runCurrent()
        repeat(1000) { publisher.offer(Location(it.toDouble(), 2.0)) }
        runCurrent()
        assertEquals(1, sent.size)
        gate.complete(Unit); runCurrent(); advanceTimeBy(10_000); runCurrent()
        assertEquals(listOf(Location(1.0, 1.0), Location(999.0, 2.0)), sent)
        publisher.close()
        publisher.offer(Location(3.0, 3.0)); advanceTimeBy(60_000); runCurrent()
        assertEquals(2, sent.size)
    }

    @Test fun foregroundStationaryFixesStillRefreshBeforeServerExpiry() = runTest {
        var calls = 0
        val publisher = LocationPublisher(backgroundScope) { calls++ }
        repeat(12) {
            publisher.offer(Location(-23.0, -46.0)); runCurrent(); advanceTimeBy(5_000)
        }
        runCurrent()
        assertEquals(7, calls)
        publisher.close()
    }
}
