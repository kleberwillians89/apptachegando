package org.example.project.core.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.example.project.features.demo.DemoRouteSession
import org.example.project.features.vendor.err.RouteDraftState
import org.example.project.core.utils.Location

class DemoRouteSessionTest {
    @Test fun syntheticSamplesUseOriginalRecorderDistanceAndStopRules() {
        val session = DemoRouteSession()
        session.sampleNext(); assertTrue(session.draft.value.waypoints.isEmpty())
        session.startRecording(); repeat(6) { session.sampleNext() }
        assertEquals(session.syntheticPoints, session.draft.value.routePoints)
        session.finishRecording(); session.sampleNext()
        assertFalse(session.draft.value.isRecording); assertEquals(6, session.draft.value.waypoints.size)
    }
    @Test fun cannotSaveDuringRecordingOrWithInsufficientPoints() = runTest {
        val session = DemoRouteSession()
        assertFailsWith<IllegalStateException> { session.save() }
        session.startRecording(); repeat(3) { session.sampleNext() }
        assertFailsWith<IllegalStateException> { session.save() }
        assertTrue(session.routes.first().isEmpty())
    }
    @Test fun repositorySavesAndRecoversTheSameGeometryOnlyWithinSession() = runTest {
        val session = DemoRouteSession(); session.startRecording(); repeat(6) { session.sampleNext() }; session.finishRecording()
        val original = session.draft.value.waypoints
        val id = session.save(); session.startRecording(); session.finishRecording()
        assertTrue(session.draft.value.waypoints.isEmpty())
        val recovered = session.reuse(id)
        assertEquals(original, recovered.waypoints); assertEquals(original, session.draft.value.waypoints)
        assertFalse(recovered.isActive); assertTrue(recovered.influencePolygon.isEmpty())
        assertFailsWith<IllegalStateException> { DemoRouteSession().reuse(id) }
        assertFailsWith<IllegalStateException> { session.reuse("remote-route-id") }
    }
    @Test fun recorderDiscardsInvalidFixesAndRespectsDatabasePointLimit() {
        val initial = RouteDraftState().startRecording(null)
        for (point in listOf(Location(Double.NaN, 0.0), Location(91.0, 0.0), Location(0.0, Double.POSITIVE_INFINITY), Location(0.0, -181.0))) {
            assertEquals(initial, initial.recordLocation(point))
        }
        var full = initial
        repeat(2001) { full = full.addPoint(Location(-23.0, -46.0)) }
        assertEquals(2000, full.waypoints.size)
    }
}
