package org.example.project.core.domain.route

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.example.project.core.domain.models.*
import org.example.project.core.utils.Location
import org.example.project.features.demo.DemoAutomaticAttendance

class RouteAttendanceTest {
    private fun route() = VendorRoute("demo-route-1", "demo-route-owner", "Rota U", DemoAutomaticAttendance.fixtureWaypoints, emptyList())
    private fun event(e: RouteAttendance, id: String, fraction: Double, revision: Long = id.filter(Char::isDigit).toLongOrNull() ?: 1L, consumer: String = id) =
        RouteInterestUpdate(e.route.vendorId, PurchaseIntent(id, consumer, e.geometry.locationAt(e.geometry.lengthMeters * fraction)), revision)
    private suspend fun ready(): RouteAttendance = RouteAttendance(route()).also {
        it.start(); it.gps(RouteGpsFix(it.geometry.locationAt(0.0), 5.0, 100_000), 100_000)
    }
    @Test fun projectionUsesEveryClampedSegmentAndCumulativeTravelOrder() {
        val g = RouteProgress(route().waypoints)
        val a = g.project(g.locationAt(g.lengthMeters * .35))!!
        val b = g.project(g.locationAt(g.lengthMeters * .90))!!
        assertEquals(g.lengthMeters * .35, a.alongMeters, .05)
        assertEquals(g.lengthMeters * .90, b.alongMeters, .05)
        assertTrue(a.crossTrackMeters < .1 && b.crossTrackMeters < .1)
        assertTrue(a.alongMeters < b.alongMeters)
        val origin = g.locationAt(0.0); val beyond = origin.copy(longitude = origin.longitude - .02)
        assertEquals(0.0, g.project(beyond)!!.alongMeters, .01)
        assertTrue(g.project(beyond)!!.crossTrackMeters > 1500)
    }
    @Test fun orderFollowsRouteEvenWhenLaterCustomerIsCloserInStraightLine() = runTest {
        val e = ready(); val early = event(e, "early", .40); val later = event(e, "later", .98, 2)
        val distance = org.example.project.core.utils.GeoUtils
        assertTrue(distance.calculateDistanceMeters(e.geometry.locationAt(0.0), later.intent.location) < distance.calculateDistanceMeters(e.geometry.locationAt(0.0), early.intent.location))
        e.upsert(listOf(later, early))
        assertEquals(listOf("early", "later"), e.state.value.queue.map { it.intent.id })
    }
    @Test fun incomingIsAutomaticAndNeverChangesCanonicalPendingToAccepted() = runTest {
        val e = ready(); e.upsert(listOf(event(e, "1", .3)))
        assertEquals(AttendanceStatus.InVendorList, e.state.value.entries.single().status)
        assertEquals("pending", e.state.value.entries.single().intent.status)
    }
    @Test fun advancePreservesBehindRecordsAndDoesNotRestartOrDuplicateInterests() = runTest {
        val e = ready(); val updates = listOf(event(e, "1", .1), event(e, "2", .65, 2), event(e, "3", .9, 3))
        e.upsert(updates)
        e.gps(RouteGpsFix(e.geometry.locationAt(e.geometry.lengthMeters * .3), 5.0, 130_000), 130_000)
        repeat(30) { e.upsert(updates) }
        assertEquals(3, e.state.value.entries.size)
        assertEquals(AttendanceStatus.Behind, e.state.value.entries.first { it.intent.id == "1" }.status)
        assertEquals(listOf("2", "3"), e.state.value.queue.map { it.intent.id })
        val progress = e.state.value.progressMeters; e.start()
        assertEquals(progress, e.state.value.progressMeters)
    }
    @Test fun jitterAndOutOfOrderGpsNeverMoveProgressBackwards() = runTest {
        val e = ready(); val middle = e.geometry.lengthMeters * .3
        e.gps(RouteGpsFix(e.geometry.locationAt(middle), 5.0, 130_000), 130_000)
        e.gps(RouteGpsFix(e.geometry.locationAt(middle - 10), 5.0, 135_000), 135_000)
        e.gps(RouteGpsFix(e.geometry.locationAt(0.0), 5.0, 110_000), 135_000)
        assertEquals(middle, e.state.value.progressMeters, .1)
    }
    @Test fun inaccurateStaleOffRouteAndImplausibleJumpSuspendEligibility() = runTest {
        val e = ready(); e.upsert(listOf(event(e, "1", .8)))
        e.gps(RouteGpsFix(e.geometry.locationAt(0.0), 300.0, 101_000), 101_000)
        assertEquals(GpsIssue.LowAccuracy, e.state.value.gpsIssue); assertTrue(e.state.value.queue.isEmpty())
        e.gps(RouteGpsFix(e.geometry.locationAt(0.0), 5.0, 102_000), 102_000)
        e.gps(RouteGpsFix(e.geometry.locationAt(e.geometry.lengthMeters), 5.0, 103_000), 103_000)
        assertEquals(GpsIssue.ImplausibleJump, e.state.value.gpsIssue); assertEquals(0.0, e.state.value.progressMeters, .1)
        val far = e.geometry.locationAt(0.0).copy(latitude = -23.5)
        e.gps(RouteGpsFix(far, 5.0, 104_000), 104_000); assertEquals(GpsIssue.OffRoute, e.state.value.gpsIssue)
        e.gps(RouteGpsFix(e.geometry.locationAt(0.0), 5.0, 105_000), 105_000)
        e.tick(136_000); assertEquals(GpsIssue.Stale, e.state.value.gpsIssue)
        e.gps(RouteGpsFix(e.geometry.locationAt(0.0), 5.0, 106_000), 150_000)
        assertEquals(GpsIssue.Stale, e.state.value.gpsIssue)
    }
    @Test fun missingGpsIsExplicitAndRecoveryRestoresSameRecords() = runTest {
        val e = ready(); e.upsert(listOf(event(e, "1", .8)))
        e.gpsUnavailable(); assertEquals(AttendanceStatus.NoGps, e.state.value.entries.single().status)
        e.gps(RouteGpsFix(e.geometry.locationAt(0.0), 5.0, 110_000), 110_000)
        assertEquals(1, e.state.value.queue.size)
    }
    @Test fun offCorridorInvalidAndAmbiguousInterestsHaveExplicitStates() = runTest {
        val e = ready()
        val far = event(e, "1", .5).let { it.copy(intent = it.intent.copy(location = it.intent.location.copy(latitude = -23.5))) }
        val invalid = event(e, "2", .5, 2).let { it.copy(intent = it.intent.copy(location = Location(Double.NaN, 0.0))) }
        e.upsert(listOf(far, invalid))
        assertEquals(AttendanceStatus.OutsideRoute, e.state.value.entries[0].status)
        assertEquals(AttendanceStatus.InvalidPoint, e.state.value.entries[1].status)
        val crossing = route().copy(waypoints = listOf(
            RouteWaypoint(-23.55, -46.64, 0), RouteWaypoint(-23.54, -46.63, 1),
            RouteWaypoint(-23.55, -46.63, 2), RouteWaypoint(-23.54, -46.64, 3)))
        val g = RouteProgress(crossing.waypoints)
        assertTrue(g.project(Location(-23.545, -46.635))!!.ambiguous)
        val x = RouteAttendance(crossing); x.start(); x.gps(RouteGpsFix(Location(-23.545, -46.635), 5.0, 1000), 1000)
        assertEquals(GpsIssue.Ambiguous, x.state.value.gpsIssue)
    }
    @Test fun versionedCancellationAndReplayNeverResurrectCancelledProtocol() = runTest {
        val e = ready(); val first = event(e, "1", .5)
        e.upsert(listOf(first)); e.upsert(listOf(first.copy(revision = 3, cancelled = true)))
        e.upsert(listOf(first, first.copy(revision = 4)))
        assertEquals(AttendanceStatus.Cancelled, e.state.value.entries.single().status)
        assertTrue(e.state.value.queue.isEmpty())
    }
    @Test fun duplicateConsumerHasOneQueueEntryAndCancellationCoversOlderCopies() = runTest {
        val e = ready(); val a = event(e, "A", .5, 1, "same"); val b = event(e, "B", .6, 2, "same")
        e.upsert(listOf(b, a)); assertEquals(1, e.state.value.queue.size)
        assertEquals(AttendanceStatus.Duplicate, e.state.value.entries.single { it.intent.id == "B" }.status)
        e.upsert(listOf(a.copy(revision = 3, cancelled = true)))
        assertTrue(e.state.value.queue.isEmpty()); assertTrue(e.state.value.entries.all { it.status == AttendanceStatus.Cancelled })
        e.upsert(listOf(event(e, "C", .7, 4, "same"))); assertEquals("C", e.state.value.queue.single().intent.id)
    }
    @Test fun pauseResumeAndEndPreserveRecordsWithoutManualAcceptance() = runTest {
        val e = ready(); e.upsert(listOf(event(e, "1", .5)))
        e.pause(); assertEquals(AttendanceStatus.Paused, e.state.value.entries.single().status)
        val before = e.state.value.progressMeters; e.gps(RouteGpsFix(e.geometry.locationAt(200.0), 5.0, 130_000), 130_000)
        assertEquals(before, e.state.value.progressMeters)
        e.resume(); assertEquals(1, e.state.value.queue.size)
        e.end(); assertEquals(AttendanceStatus.JourneyEnded, e.state.value.entries.single().status)
        assertTrue(e.state.value.queue.isEmpty()); assertFailsWith<IllegalStateException> { e.start() }
    }
    @Test fun offlineSnapshotReconciliationKeepsDeltasAndCancellationAndMissingReasons() = runTest {
        val e = ready(); val a = event(e, "1", .5); val b = event(e, "2", .7, 2)
        e.upsert(listOf(a, b)); e.connection(false)
        assertTrue(e.state.value.entries.all { it.status == AttendanceStatus.NoConnection })
        e.upsert(listOf(a.copy(revision = 11, cancelled = true)))
        e.reconcile(listOf(a, b), 5); e.connection(true)
        assertEquals(AttendanceStatus.Cancelled, e.state.value.entries.first { it.intent.id == "1" }.status)
        assertEquals(listOf("2"), e.state.value.queue.map { it.intent.id })
        e.reconcile(emptyList(), 20)
        assertEquals(AttendanceStatus.Unavailable, e.state.value.entries.first { it.intent.id == "2" }.status)
        e.reconcile(listOf(b), 10) // Older authoritative snapshot ignored.
        assertTrue(e.state.value.queue.isEmpty())
        e.reconcile(listOf(b), 21) // A newer complete snapshot may restore a missing, unchanged row.
        assertEquals(listOf("2"), e.state.value.queue.map { it.intent.id })
    }
    @Test fun foreignVendorAndIdentityMutationAreRejectedAtomically() = runTest {
        val e = ready(); val a = event(e, "1", .5)
        assertFailsWith<IllegalArgumentException> { e.upsert(listOf(a, a.copy(vendorId = "other"))) }
        assertTrue(e.state.value.entries.isEmpty())
        e.upsert(listOf(a))
        assertFailsWith<IllegalArgumentException> { e.upsert(listOf(a.copy(revision = 2, intent = a.intent.copy(consumerId = "other")))) }
        assertEquals("1", e.state.value.entries.single().intent.consumerId)
    }
    @Test fun existingCanonicalAcceptedFulfilledExpiredNeverBecomeAutomaticSales() = runTest {
        val e = ready()
        e.upsert(listOf("accepted", "fulfilled", "expired").mapIndexed { i, status ->
            event(e, "$i", .5, i.toLong()).let { it.copy(intent = it.intent.copy(status = status)) }
        })
        assertTrue(e.state.value.queue.isEmpty()); assertTrue(e.state.value.entries.all { it.status == AttendanceStatus.Unavailable })
    }
    @Test fun scalesOf10_90_450KeepOrderIdsAndCancellationAfterFrequentGps() = runTest {
        for (count in listOf(10, 90, 450)) {
            val mark = kotlin.time.TimeSource.Monotonic.markNow()
            val e = ready()
            val events = (1..count).map { event(e, "$it", .05 + .9 * it / count, it.toLong()) }
            e.upsert(events.reversed()); assertEquals(count, e.state.value.queue.size)
            assertEquals(events.map { it.intent.id }, e.state.value.queue.map { it.intent.id })
            repeat(100) { i ->
                e.gps(RouteGpsFix(e.geometry.locationAt(e.geometry.lengthMeters * .7 * (i + 1) / 100), 5.0, 100_000L + (i + 1) * 5000), 100_000L + (i + 1) * 5000)
                e.upsert(events)
            }
            assertEquals(count, e.state.value.entries.size)
            assertEquals(count, e.state.value.entries.map { it.intent.id }.distinct().size)
            assertTrue(e.state.value.queue.all { it.alongMeters!! >= e.state.value.progressMeters })
            val cancel = events.last().copy(revision = count + 1000L, cancelled = true)
            e.upsert(listOf(cancel)); e.connection(false); e.reconcile(events.dropLast(1) + cancel, count + 1000L); e.connection(true)
            assertEquals(AttendanceStatus.Cancelled, e.state.value.entries.single { it.intent.id == cancel.intent.id }.status)
            e.end(); assertTrue(e.state.value.queue.isEmpty())
            println("ROUTE_SCALE clients=$count gps_updates=100 replays=100 elapsed_ms=${mark.elapsedNow().inWholeMilliseconds}")
        }
    }
    @Test fun simultaneousDuplicateBurstsAreSerializedByTheSameReducer() = runTest {
        val e = ready(); val updates = (1..450).map { event(e, "$it", .1 + .8 * it / 450, it.toLong()) }
        (updates + updates).map { update -> async(kotlinx.coroutines.Dispatchers.Default) { e.upsert(listOf(update)) } }.awaitAll()
        assertEquals(450, e.state.value.entries.size); assertEquals(450, e.state.value.queue.size)
    }
    @Test fun changedConsumerPositionReprojectsWithoutDuplicatingOrResettingProgress() = runTest {
        val e = ready(); val initial = event(e, "1", .8)
        e.upsert(listOf(initial))
        e.gps(RouteGpsFix(e.geometry.locationAt(e.geometry.lengthMeters * .3), 5.0, 130_000), 130_000)
        val moved = initial.copy(revision = 2, intent = initial.intent.copy(location = e.geometry.locationAt(e.geometry.lengthMeters * .1)))
        e.upsert(listOf(moved))
        assertEquals(AttendanceStatus.Behind, e.state.value.entries.single().status)
        assertEquals(1, e.state.value.entries.size)
        assertEquals(e.geometry.lengthMeters * .3, e.state.value.progressMeters, .1)
        e.upsert(listOf(initial))
        assertEquals(AttendanceStatus.Behind, e.state.value.entries.single().status)
    }
    @Test fun denseRecordedRouteWith2000PointsAnd450ClientsKeepsOrderAcrossGpsUpdates() = runTest {
        val mark = kotlin.time.TimeSource.Monotonic.markNow()
        val coarse = RouteProgress(route().waypoints)
        val dense = route().copy(waypoints = (0 until 2000).map { index ->
            val p = coarse.locationAt(coarse.lengthMeters * index / 1999)
            RouteWaypoint(p.latitude, p.longitude, index)
        })
        val e = RouteAttendance(dense); e.start()
        e.gps(RouteGpsFix(e.geometry.locationAt(0.0), 5.0, 100_000), 100_000)
        val updates = (1..450).map { event(e, "$it", .1 + .8 * it / 450, it.toLong()) }
        e.upsert(updates.reversed())
        assertEquals(450, e.state.value.queue.size)
        assertEquals(updates.map { it.intent.id }, e.state.value.queue.map { it.intent.id })
        repeat(100) { index ->
            val time = 100_000L + (index + 1) * 5000
            e.gps(RouteGpsFix(e.geometry.locationAt(e.geometry.lengthMeters * .6 * (index + 1) / 100), 5.0, time), time)
        }
        assertEquals(450, e.state.value.entries.size)
        assertTrue(e.state.value.queue.all { it.alongMeters!! >= e.state.value.progressMeters })
        println("ROUTE_DENSE vertices=2000 clients=450 gps_updates=100 elapsed_ms=${mark.elapsedNow().inWholeMilliseconds}")
    }
    @Test fun invalidRoutesCannotStartOrPretendToBePersistedGeometry() {
        assertFailsWith<IllegalArgumentException> { RouteProgress(listOf(RouteWaypoint(0.0, 0.0, 0), RouteWaypoint(0.0, 0.0, 1))) }
        assertFailsWith<IllegalArgumentException> { RouteProgress(listOf(RouteWaypoint(91.0, 0.0, 0), RouteWaypoint(0.0, 0.0, 1))) }
        assertFailsWith<IllegalArgumentException> { RouteProgress(listOf(RouteWaypoint(0.0, 0.0, 0), RouteWaypoint(0.0, 0.01, 0))) }
    }
}
