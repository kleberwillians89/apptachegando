package org.example.project.core.domain.route

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import org.example.project.features.demo.DemoAutomaticAttendance
import org.example.project.features.demo.DemoRouteSession

/** Reuses the existing simulated transport and real attendance reducer; no Auth/device/tiles. */
class DevelopmentPlaybackTest {
    private suspend fun session(): DemoAutomaticAttendance {
        val routes = DemoRouteSession()
        val id = routes.savePreparedRoute("Percurso local", DemoAutomaticAttendance.fixtureWaypoints)
        return DemoAutomaticAttendance(routes).also { it.select(id); it.start() }
    }
    private suspend fun trace(): List<AttendanceSnapshot> {
        val demo = session(); val result = mutableListOf<AttendanceSnapshot>()
        fun record() { result += demo.attendance.value!!.state.value }
        demo.simulateClients(10); record()
        demo.replayEvents(); record()
        demo.advance(); record()
        demo.pause(); record()
        demo.resume(); record()
        demo.toggleConnection(); record()
        demo.toggleConnection(); record()
        return result
    }
    @Test fun sameInputsProduceIdenticalPositionsQueueAndProtocols() = runTest {
        val a = trace(); val b = trace()
        assertEquals(a,b)
        assertEquals(a[0].entries, a[1].entries)
        assertTrue(a[2].progressMeters > a[1].progressMeters)
        assertTrue(a[3].queue.isEmpty())
        assertEquals(JourneyPhase.Working, a.last().phase)
        assertTrue(a.last().connected)
    }
    @Test fun resetHasNoPersistenceOrInterestLeakBetweenDevelopmentSessions() = runTest {
        val first = session(); first.simulateClients(10); first.advance()
        val fresh = session().attendance.value!!.state.value
        assertEquals(0.0, fresh.progressMeters)
        assertTrue(fresh.entries.isEmpty())
        assertEquals(DemoAutomaticAttendance.fixtureWaypoints.first().latitude, fresh.location!!.latitude)
    }
    @Test fun offlineCancellationReconcilesWithoutIndividualAcceptance() = runTest {
        val demo = session()
        val id = demo.consumerInterest(demo.meetingPoint(0))
        demo.toggleConnection(); demo.cancelConsumerInterest()
        assertTrue(demo.consumerCancellationPending.value)
        demo.toggleConnection()
        val state = demo.attendance.value!!.state.value
        assertTrue(state.connected)
        assertFalse(demo.consumerCancellationPending.value)
        assertTrue(state.queue.none { it.intent.id == id })
        assertEquals(AttendanceStatus.Cancelled, state.entries.single { it.intent.id == id }.status)
    }
}
