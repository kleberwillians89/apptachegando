package org.example.project.core.domain.route

import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.example.project.features.demo.*

class DemoAutomaticAttendanceTest {
    private class Store : DemoRouteStorage {
        var data: String? = null
        var failWrite = false
        override suspend fun read(): String? = data
        override suspend fun write(value: String) { check(!failWrite); data = value }
    }
    @Test fun originalRouteRepositoryPersistsAndLoadsSyntheticRouteAcrossInstances() = runTest {
        val store = Store(); val first = DemoRouteSession(store)
        val id = first.savePreparedRoute("Rota fictícia salva", DemoAutomaticAttendance.fixtureWaypoints)
        assertNotNull(store.data)
        val reopened = DemoRouteSession(store); reopened.restoreSavedRoutes()
        assertEquals(id, reopened.routes.value.single().id)
        assertEquals(DemoAutomaticAttendance.fixtureWaypoints, reopened.reuse(id).waypoints)
        val auto = DemoAutomaticAttendance(reopened); auto.select(id); auto.start()
        assertEquals(JourneyPhase.Working, auto.attendance.value!!.state.value.phase)
        assertFalse(reopened.routes.value.single().isActive) // No remote active route was invented.
    }
    @Test fun failedPersistenceNeverCreatesFalseSavedSuccess() = runTest {
        val store = Store().apply { failWrite = true }; val routes = DemoRouteSession(store)
        assertFailsWith<IllegalStateException> { routes.savePreparedRoute("Rota", DemoAutomaticAttendance.fixtureWaypoints) }
        assertTrue(routes.routes.value.isEmpty()); assertNull(store.data)
    }
    @Test fun corruptArchiveIsPreservedAndNotSilentlyReset() = runTest {
        val store = Store().apply { data = "invalid-json" }; val routes = DemoRouteSession(store)
        assertFails { routes.restoreSavedRoutes() }
        assertEquals("invalid-json", store.data); assertTrue(routes.routes.value.isEmpty())
    }
    @Test fun realOwnerOrIdsCannotEnterPrivateSyntheticArchive() = runTest {
        val store = Store(); val routes = DemoRouteSession(store)
        routes.savePreparedRoute("Rota", DemoAutomaticAttendance.fixtureWaypoints)
        val original = store.data!!
        store.data = original.replace("demo-route-owner", "real-owner")
        assertFailsWith<IllegalArgumentException> { DemoRouteSession(store).restoreSavedRoutes() }
        store.data = original.replace("demo-route-1", "real-route-id")
        assertFailsWith<IllegalArgumentException> { DemoRouteSession(store).restoreSavedRoutes() }
        store.data = original.replace("\"version\":1", "\"version\":2")
        // Kotlin serialization omits default version. Inject the unsupported version explicitly.
        store.data = "{\"version\":2," + original.removePrefix("{")
        assertFailsWith<IllegalArgumentException> { DemoRouteSession(store).restoreSavedRoutes() }
    }
    @Test fun legacyDemoRoutesAndInterestsRemainEphemeralByDefault() = runTest {
        val first = DemoRouteSession(); first.savePreparedRoute("Sessão", DemoAutomaticAttendance.fixtureWaypoints)
        val fresh = DemoRouteSession(); fresh.restoreSavedRoutes()
        assertTrue(fresh.routes.value.isEmpty())
    }
    private suspend fun demo(): DemoAutomaticAttendance {
        val routes = DemoRouteSession(Store())
        val id = routes.savePreparedRoute("Rota fictícia", DemoAutomaticAttendance.fixtureWaypoints)
        return DemoAutomaticAttendance(routes).also { it.select(id); it.start() }
    }
    @Test fun tenAutomaticInterestsReplayAdvanceAndPauseWithoutAcceptButtons() = runTest {
        val demo = demo(); demo.simulateClients(10)
        val engine = demo.attendance.value!!
        assertEquals(10, engine.state.value.queue.size)
        demo.replayEvents(); assertEquals(10, engine.state.value.entries.size)
        demo.advance(); assertEquals(7, engine.state.value.queue.size)
        assertEquals(3, engine.state.value.entries.count { it.status == AttendanceStatus.Behind })
        demo.pause(); assertTrue(engine.state.value.queue.isEmpty())
        assertTrue(engine.state.value.entries.all { it.status == AttendanceStatus.Paused })
        demo.resume(); assertEquals(7, engine.state.value.queue.size)
    }
    @Test fun reconnectReceivesFullWireSnapshotAndPreservesOnlyOneRecordPerId() = runTest {
        val demo = demo(); val e = demo.attendance.value!!
        demo.simulateClients(10); demo.toggleConnection(); demo.simulateClients(10); demo.replayEvents()
        assertEquals(10, e.state.value.entries.size)
        assertTrue(e.state.value.entries.all { it.status == AttendanceStatus.NoConnection })
        demo.toggleConnection(); assertEquals(20, e.state.value.entries.size)
        assertEquals(20, e.state.value.queue.size)
        repeat(3) { demo.replayEvents() }; assertEquals(20, e.state.value.entries.size)
    }
    @Test fun consumerExplicitMeetingPointStateCancellationAndEndAreSharedLocally() = runTest {
        val demo = demo(); val e = demo.attendance.value!!
        val first = demo.consumerInterest(demo.meetingPoint(0))
        assertEquals(AttendanceStatus.InVendorList, e.state.value.entries.single().status)
        assertEquals(first, demo.consumerInterest(demo.meetingPoint(0)))
        demo.cancelConsumerInterest(); assertEquals(AttendanceStatus.Cancelled, e.state.value.entries.single().status)
        val second = demo.consumerInterest(demo.meetingPoint(2)); assertNotEquals(first, second)
        assertEquals(AttendanceStatus.OutsideRoute, e.state.value.entries.single { it.intent.id == second }.status)
        demo.end(); assertEquals(AttendanceStatus.JourneyEnded, e.state.value.entries.single { it.intent.id == second }.status)
        assertTrue(e.state.value.queue.isEmpty())
    }
    @Test fun offlineConsumerCancellationIsPendingUntilReconciledAndNoDuplicateAppears() = runTest {
        val demo = demo(); val id = demo.consumerInterest(demo.meetingPoint(0)); demo.toggleConnection()
        demo.cancelConsumerInterest(); assertTrue(demo.consumerCancellationPending.value)
        assertEquals(AttendanceStatus.NoConnection, demo.attendance.value!!.state.value.entries.single().status)
        demo.toggleConnection(); assertFalse(demo.consumerCancellationPending.value)
        assertEquals(AttendanceStatus.Cancelled, demo.attendance.value!!.state.value.entries.single { it.intent.id == id }.status)
    }
    @Test fun reusingSavedRouteStartsNewJourneyAndKeepsPreviousHistory() = runTest {
        val demo = demo(); val id = demo.attendance.value!!.route.id
        demo.simulateClients(10); demo.end(); demo.chooseRouteAgain()
        assertNull(demo.attendance.value)
        assertEquals(10, demo.previousJourneys.value.single().entries.size)
        assertEquals(id, demo.routes.routes.value.single().id)
        demo.select(id); demo.start()
        assertTrue(demo.attendance.value!!.state.value.entries.isEmpty())
        assertEquals(10, demo.previousJourneys.value.single().entries.size)
    }
    @Test fun behindMeetingPointNeverBecomesAnEligibleCustomerAtRouteStartOrAfterAdvance() = runTest {
        val demo = demo(); val e = demo.attendance.value!!
        val beforeStart = demo.consumerInterest(demo.meetingPoint(1))
        assertEquals(AttendanceStatus.OutsideRoute, e.state.value.entries.single { it.intent.id == beforeStart }.status)
        demo.cancelConsumerInterest(); demo.advance()
        val behind = demo.consumerInterest(demo.meetingPoint(1))
        assertEquals(AttendanceStatus.Behind, e.state.value.entries.single { it.intent.id == behind }.status)
        assertTrue(e.state.value.queue.isEmpty())
    }
    @Test fun offlineAdvanceReconcilesNewInterestsAtCurrentProgressInsteadOfRestartingRoute() = runTest {
        val demo = demo(); val e = demo.attendance.value!!
        demo.simulateClients(10); demo.toggleConnection(); demo.advance(); demo.simulateClients(90)
        val progress = e.state.value.progressMeters
        demo.toggleConnection(); assertEquals(100, e.state.value.entries.size)
        assertEquals(progress, e.state.value.progressMeters)
        assertTrue(e.state.value.entries.any { it.status == AttendanceStatus.Behind })
        assertTrue(e.state.value.queue.all { it.alongMeters!! >= progress })
        demo.replayEvents(); assertEquals(100, e.state.value.entries.size)
    }
    @Test fun confirmedMeetingPointChangeUpdatesSameProtocolAndDerivedQueue() = runTest {
        val demo = demo(); val e = demo.attendance.value!!
        val first = demo.consumerInterest(demo.meetingPoint(0))
        assertEquals(AttendanceStatus.InVendorList, e.state.value.entries.single().status)
        assertEquals(first, demo.consumerInterest(demo.meetingPoint(2)))
        assertEquals(AttendanceStatus.OutsideRoute, e.state.value.entries.single().status)
        assertEquals(1, e.state.value.entries.size)
        demo.toggleConnection()
        assertEquals(first, demo.consumerInterest(demo.meetingPoint(0)))
        demo.toggleConnection()
        assertEquals(AttendanceStatus.InVendorList, e.state.value.entries.single().status)
        assertEquals(first, e.state.value.queue.single().intent.id)
    }
    @Test fun signalLossAndRecoveryDoNotRecreateAnyInterest() = runTest {
        val demo = demo(); demo.simulateClients(10); val e = demo.attendance.value!!
        demo.loseGps(); assertTrue(e.state.value.entries.all { it.status == AttendanceStatus.NoGps })
        demo.restoreGps(); assertEquals(10, e.state.value.queue.size); assertEquals(10, e.state.value.entries.size)
    }
}
