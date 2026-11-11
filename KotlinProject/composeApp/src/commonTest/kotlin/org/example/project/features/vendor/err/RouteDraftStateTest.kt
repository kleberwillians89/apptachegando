package org.example.project.features.vendor.err

import org.example.project.core.utils.Location
import org.example.project.core.domain.route.RouteGpsFix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteDraftStateTest {
    @Test
    fun mountainViewToSaoPauloStopsRecordingWithoutJoiningOrErasingPoints() {
        val california = Location(37.4219983, -122.084)
        val first = RouteDraftState().startRecording(null).recordFix(RouteGpsFix(california, 5.0, 100_000), 100_000)
        val rejected = first.recordFix(RouteGpsFix(Location(-23.5505, -46.6333), 5.0, 105_000), 105_000)
        assertFalse(rejected.isRecording)
        assertEquals(first.waypoints, rejected.waypoints)
        assertTrue(rejected.gpsWarning != null)
        assertFalse(rejected.canStartRoute)
    }

    @Test
    fun staleInaccurateInvalidAndOutOfOrderFixesNeverBecomeRoutePoints() {
        val start = RouteDraftState().startRecording(null)
        val point = Location(-23.5505, -46.6333)
        assertTrue(start.recordFix(RouteGpsFix(point, 5.0, 1), 100_000).waypoints.isEmpty())
        assertTrue(start.recordFix(RouteGpsFix(point, 101.0, 100_000), 100_000).waypoints.isEmpty())
        assertTrue(start.recordFix(RouteGpsFix(Location(Double.NaN, 0.0), 5.0, 100_000), 100_000).waypoints.isEmpty())
        val first = start.recordFix(RouteGpsFix(point, 5.0, 100_000), 100_000)
        assertEquals(first, first.recordFix(RouteGpsFix(point, 5.0, 99_000), 100_000))
        val next = first.recordFix(RouteGpsFix(Location(-23.5510, -46.6340), 5.0, 105_000), 105_000)
        assertEquals(2, next.waypoints.size)
        assertTrue(next.isRecording)
    }

    @Test
    fun repeatedRecordingCommandDoesNotEraseCapturedPoints() {
        val recording = RouteDraftState()
            .startRecording(Location(-23.5505, -46.6333))
            .recordLocation(Location(-23.5510, -46.6340))
        assertEquals(recording, recording.startRecording(null))
    }

    @Test
    fun unavailableGpsAndInvalidCoordinatesDoNotInventPoints() {
        val waiting = RouteDraftState().startRecording(null)
        assertFalse(waiting.canStartRoute)
        assertEquals(waiting, waiting.recordLocation(Location(Double.NaN, -46.0)))
        assertEquals(waiting, waiting.recordLocation(Location(-91.0, -46.0)))
        val recovered = waiting.recordLocation(Location(-23.5505, -46.6333))
            .recordLocation(Location(-23.5510, -46.6340))
        assertTrue(recovered.canStartRoute)
        assertEquals(2, recovered.waypoints.size)
    }

    @Test
    fun defaultsToRecordingModeForNonTechnicalVendor() {
        val state = RouteDraftState()

        assertEquals(RouteCreationMode.Recording, state.creationMode)
        assertFalse(state.isRecording)
        assertEquals("Aperte gravar e faca seu caminho normal.", state.guidanceText)
    }

    @Test
    fun recordsRouteFromGpsUpdatesAndSkipsTinyMovement() {
        val state = RouteDraftState()
            .startRecording(Location(-23.5505, -46.6333))
            .recordLocation(Location(-23.55051, -46.63331))
            .recordLocation(Location(-23.5510, -46.6340))

        assertTrue(state.isRecording)
        assertEquals(2, state.waypoints.size)
        assertEquals(0, state.waypoints.first().orderIndex)
        assertEquals(1, state.waypoints.last().orderIndex)
        assertTrue(state.canStartRoute)
        assertEquals("2 pontos gravados. Continue seu caminho.", state.guidanceText)
    }

    @Test
    fun finishesRecordedRouteWithoutLosingGpsPoints() {
        val state = RouteDraftState()
            .startRecording(Location(-23.5505, -46.6333))
            .recordLocation(Location(-23.5510, -46.6340))
            .finishRecording()

        assertFalse(state.isRecording)
        assertEquals(2, state.waypoints.size)
        assertEquals("Rota gravada. Revise e salve.", state.guidanceText)
    }

    @Test
    fun keepsManualMapModeAsAdvancedOption() {
        val state = RouteDraftState()
            .switchToManual()
            .addPoint(Location(-23.5505, -46.6333))
            .addPoint(Location(-23.5510, -46.6340))

        assertEquals(RouteCreationMode.Manual, state.creationMode)
        assertTrue(state.canStartRoute)
        assertEquals("2 pontos marcados. Continue tocando nas esquinas.", state.guidanceText)
    }

    @Test
    fun addsTouchedMapPointsAsOrderedWaypoints() {
        val state = RouteDraftState(creationMode = RouteCreationMode.Manual)
            .addPoint(Location(-23.5505, -46.6333))
            .addPoint(Location(-23.5510, -46.6340))

        assertEquals(2, state.waypoints.size)
        assertEquals(0, state.waypoints.first().orderIndex)
        assertEquals(1, state.waypoints.last().orderIndex)
        assertTrue(state.canStartRoute)
    }

    @Test
    fun undoAndClearRoutePoints() {
        val state = RouteDraftState(creationMode = RouteCreationMode.Manual)
            .addPoint(Location(-23.5505, -46.6333))
            .addPoint(Location(-23.5510, -46.6340))
            .removeLastPoint()

        assertEquals(1, state.waypoints.size)
        assertEquals(0, state.clear().waypoints.size)
    }

    @Test
    fun closesRouteByReturningToStartPoint() {
        val state = RouteDraftState(creationMode = RouteCreationMode.Manual)
            .addPoint(Location(-23.5505, -46.6333))
            .addPoint(Location(-23.5505, -46.6340))
            .addPoint(Location(-23.5510, -46.6340))
            .closeLoop()

        assertTrue(state.isClosed)
        assertEquals(4, state.waypoints.size)
        assertEquals(state.waypoints.first().latitude, state.waypoints.last().latitude)
        assertEquals(state.waypoints.first().longitude, state.waypoints.last().longitude)
        assertEquals(3, state.waypoints.last().orderIndex)
    }
}
