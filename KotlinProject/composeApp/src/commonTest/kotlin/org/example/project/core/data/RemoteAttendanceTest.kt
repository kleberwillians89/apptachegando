package org.example.project.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.example.project.core.domain.models.*
import org.example.project.core.domain.route.*
import org.example.project.core.utils.Location

class RemoteAttendanceTest {
    @Test fun ignoredOldSnapshotCannotRaiseAnotherAlertOrMismatchQueueIds() = runTest {
        val session = ready()
        val gate = org.example.project.core.domain.notifications.NewInterestDetector()
        assertTrue(gate.observe(session.attendance.state.value, true).isEmpty())
        session.apply(snapshot(item("fresh"), request = 5))
        assertEquals(setOf("fresh"), gate.observe(session.attendance.state.value, true))
        session.apply(snapshot(item("old", "b", 0.001), request = 4))
        assertEquals(emptySet(), gate.observe(session.attendance.state.value, true))
    }

    @Test fun onlyNewEligibleProtocolFromConfirmedSnapshotRaisesVisualAlert() = runTest {
        val session = ready()
        val gate = org.example.project.core.domain.notifications.NewInterestDetector()
        gate.observe(emptySet(), emptySet(), true)
        session.apply(snapshot(item("ahead"), item("behind", "b", 0.001), item("outside", "c", 0.006, 0.005)))
        val ids = setOf("ahead", "behind", "outside")
        val queue = session.attendance.state.value.queue.map { it.intent.id }.toSet()
        assertEquals(setOf("ahead"), gate.observe(ids, queue, true))
        session.apply(snapshot(item("ahead"), item("behind", "b", 0.001), item("outside", "c", 0.006, 0.005)))
        assertEquals(emptySet(), gate.observe(ids, session.attendance.state.value.queue.map { it.intent.id }.toSet(), true))
    }

    private fun route() = VendorRoute("route", "vendor", "Percurso real", listOf(
        RouteWaypoint(0.0, 0.0, 0), RouteWaypoint(0.0, 0.01, 1)
    ), emptyList(), isActive = true)
    private fun item(id: String = "interest", consumer: String = "consumer", lng: Double = 0.008, lat: Double = 0.0, status: String = "pending") =
        RemoteAttendanceIntent(id, consumer, lat, lng, status)
    private fun snapshot(vararg intents: RemoteAttendanceIntent, online: Boolean = true, active: Boolean = true, request: Long = 0) =
        RemoteAttendanceSnapshot("route", "vendor", active, online, intents.toList(), request)
    private suspend fun ready(): RemoteAttendanceSession {
        val session = RemoteAttendanceSession(RouteAttendance(route()))
        session.apply(snapshot())
        session.attendance.gps(RouteGpsFix(Location(0.0, 0.002), 5.0, 1000), 1000)
        return session
    }
    @Test fun realSnapshotUsesSameAutomaticGeometryWithoutAcceptingOrCompletingSales() = runTest {
        val session = ready()
        session.apply(snapshot(item("ahead"), item("behind", "b", 0.001), item("outside", "c", 0.006, 0.005)))
        val state = session.attendance.state.value
        assertEquals(listOf("ahead"), state.queue.map { it.intent.id })
        assertEquals(AttendanceStatus.Behind, state.entries.single { it.intent.id == "behind" }.status)
        assertEquals(AttendanceStatus.OutsideRoute, state.entries.single { it.intent.id == "outside" }.status)
        assertTrue(state.entries.all { it.intent.status == "pending" })
    }
    @Test fun cancellationDoesNotCancelANewerProtocolForTheSameConsumer() = runTest {
        val session = ready()
        session.apply(snapshot(item("old")))
        session.apply(snapshot(item("old", status = "cancelled"), item("new")))
        assertEquals(listOf("new"), session.attendance.state.value.queue.map { it.intent.id })
        assertEquals(AttendanceStatus.Cancelled, session.attendance.state.value.entries.single { it.intent.id == "old" }.status)
    }
    @Test fun missingRowIsUnavailableAndNeverAFabricatedCancellationOrSale() = runTest {
        val session = ready()
        session.apply(snapshot(item()))
        session.apply(snapshot())
        val entry = session.attendance.state.value.entries.single()
        assertEquals(AttendanceStatus.Unavailable, entry.status)
        assertEquals("pending", entry.intent.status)
    }
    @Test fun reconnectionReconcilesPreservedRecordsWithoutDuplicates() = runTest {
        val session = ready()
        session.apply(snapshot(item()))
        session.failed()
        assertFalse(session.attendance.state.value.connected)
        assertEquals(1, session.attendance.state.value.entries.size)
        assertTrue(session.attendance.state.value.queue.isEmpty())
        session.apply(snapshot(item()))
        session.apply(snapshot(item()))
        assertEquals(1, session.attendance.state.value.queue.size)
    }
    @Test fun offlinePauseAndEndedRouteNeverKeepAnEligibleQueue() = runTest {
        val session = ready()
        session.apply(snapshot(item(), online = false))
        assertEquals(JourneyPhase.Paused, session.attendance.state.value.phase)
        assertTrue(session.attendance.state.value.queue.isEmpty())
        session.apply(snapshot(item()))
        assertTrue(session.attendance.state.value.queue.isEmpty()) // Resume requires a new fix.
        session.attendance.gps(RouteGpsFix(Location(0.0, 0.002), 5.0, 2000), 2000)
        assertEquals(1, session.attendance.state.value.queue.size)
        session.apply(snapshot(active = false, online = false))
        assertEquals(JourneyPhase.Ended, session.attendance.state.value.phase)
        assertTrue(session.attendance.state.value.queue.isEmpty())
    }
    @Test fun staleOrInaccurateGpsNeverProducesAnArrivalEstimateOrEligibleQueue() = runTest {
        val session = ready()
        session.apply(snapshot(item()))
        session.attendance.tick(32_000)
        assertEquals(GpsIssue.Stale, session.attendance.state.value.gpsIssue)
        assertTrue(session.attendance.state.value.queue.isEmpty())
        session.attendance.gps(RouteGpsFix(Location(0.0, 0.003), Double.NaN, 33_000), 33_000)
        assertTrue(session.attendance.state.value.queue.isEmpty())
    }
    @Test fun wrongRouteVendorOrConsumerIdentityCannotMutateTheQueue() = runTest {
        val session = ready()
        session.apply(snapshot(item()))
        val before = session.attendance.state.value
        assertFailsWith<IllegalArgumentException> { session.apply(snapshot(item()).copy(vendorId = "other")) }
        assertFailsWith<IllegalArgumentException> { session.apply(snapshot(item()).copy(routeId = "other")) }
        assertFailsWith<IllegalArgumentException> { session.apply(snapshot(item(consumer = "attacker"))) }
        assertEquals(before, session.attendance.state.value)
    }
    @Test fun olderCompletedReadCannotOverwriteANewerPauseSnapshot() = runTest {
        val session = ready()
        session.apply(snapshot(item(), online = false, request = 5))
        session.apply(snapshot(item(), online = true, request = 4))
        assertEquals(JourneyPhase.Paused, session.attendance.state.value.phase)
    }
    @Test fun duplicateRowsAreRejectedInsteadOfSilentlyChoosingAnIdentity() = runTest {
        val session = ready()
        assertFailsWith<IllegalArgumentException> { session.apply(snapshot(item(), item())) }
    }
    @Test fun networkFailureIsNotAnEmptySuccessfulSnapshotAndCancellationPropagates() = runTest {
        val transport = object : AttendanceTransport {
            override suspend fun snapshot(routeId: String): RemoteAttendanceSnapshot = error("offline")
            override suspend fun availability(routeId: String, online: Boolean, end: Boolean) { throw CancellationException("closed") }
        }
        val repository = RouteAttendanceRepository(transport)
        assertFailsWith<IllegalStateException> { repository.refresh("route") }
        assertFailsWith<CancellationException> { repository.setAvailability("route", false) }
    }
    @Test fun readsAndJourneyCommandsUseOneSerializedTransport() = runTest {
        var activeCalls = 0
        var peak = 0
        val transport = object : AttendanceTransport {
            override suspend fun snapshot(routeId: String): RemoteAttendanceSnapshot {
                activeCalls++; peak = maxOf(peak, activeCalls); delay(10); activeCalls--
                return this@RemoteAttendanceTest.snapshot()
            }
            override suspend fun availability(routeId: String, online: Boolean, end: Boolean) {
                activeCalls++; peak = maxOf(peak, activeCalls); delay(10); activeCalls--
            }
        }
        val repository = RouteAttendanceRepository(transport)
        val reads = listOf(async { repository.refresh("route") }, async { repository.refresh("route") })
        val command = async { repository.setAvailability("route", false) }
        val results = reads.awaitAll(); command.await()
        assertEquals(1, peak)
        assertEquals(listOf(1L, 2L), results.map { it.requestSequence })
    }
    @Test fun confirmedCommandsAdvanceRequestOrderEvenBeforeRefresh() = runTest {
        val transport = object : AttendanceTransport {
            override suspend fun snapshot(routeId: String) = this@RemoteAttendanceTest.snapshot()
            override suspend fun availability(routeId: String, online: Boolean, end: Boolean) = Unit
        }
        val repository = RouteAttendanceRepository(transport)
        assertEquals(1L, repository.refresh("route").requestSequence)
        val acknowledgement: Any = repository.setAvailability("route", false)
        assertEquals(2L, acknowledgement)
        assertEquals(3L, repository.refresh("route").requestSequence)
    }

    @Test fun acknowledgedPauseSurvivesFailedRefreshAndOlderCompletedReads() = runTest {
        val session = ready()
        session.apply(snapshot(item(), request = 2))
        session.confirmAvailability(3, online = false)
        session.failed() // The follow-up snapshot failed; the command already succeeded.
        session.apply(snapshot(item(), online = true, request = 2))
        val state = session.attendance.state.value
        assertEquals(JourneyPhase.Paused, state.phase)
        assertFalse(state.connected)
        assertTrue(state.queue.isEmpty())
        assertEquals(1, state.entries.size)
        assertEquals("pending", state.entries.single().intent.status)
    }
    @Test fun confirmedResumeRequiresFreshSnapshotAndGpsBeforeQueueReturns() = runTest {
        val session = ready()
        session.apply(snapshot(item(), request = 2))
        session.confirmAvailability(3, online = false)
        session.confirmAvailability(4, online = true)
        assertEquals(JourneyPhase.Working, session.attendance.state.value.phase)
        assertTrue(session.attendance.state.value.queue.isEmpty())
        session.apply(snapshot(item(), request = 5))
        assertTrue(session.attendance.state.value.queue.isEmpty())
        session.attendance.gps(RouteGpsFix(Location(0.0, 0.002), 5.0, 2000), 2000)
        assertEquals(1, session.attendance.state.value.queue.size)
    }
    @Test fun confirmedEndIsNotUndoneByBufferedReadAndNeverFulfillsAnInterest() = runTest {
        val session = ready()
        session.apply(snapshot(item(), request = 2))
        session.confirmAvailability(3, online = false, end = true)
        session.apply(snapshot(item(), request = 2))
        assertEquals(JourneyPhase.Ended, session.attendance.state.value.phase)
        assertTrue(session.attendance.state.value.queue.isEmpty())
        assertEquals("pending", session.attendance.state.value.entries.single().intent.status)
    }
    @Test fun failedCommandDoesNotConsumeAcknowledgementOrConfirmPause() = runTest {
        val transport = object : AttendanceTransport {
            override suspend fun snapshot(routeId: String) = this@RemoteAttendanceTest.snapshot()
            override suspend fun availability(routeId: String, online: Boolean, end: Boolean) { error("timeout") }
        }
        val repository = RouteAttendanceRepository(transport)
        val session = RemoteAttendanceSession(RouteAttendance(route()))
        session.apply(repository.refresh("route"))
        assertFailsWith<IllegalStateException> { repository.setAvailability("route", false) }
        assertEquals(JourneyPhase.Working, session.attendance.state.value.phase)
        assertEquals(2L, repository.refresh("route").requestSequence)
    }

}
