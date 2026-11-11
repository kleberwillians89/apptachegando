package org.example.project.core.performance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.example.project.core.ui.maps.clusterVendorLocations
import org.example.project.core.ui.maps.routeMarkerIndices
import org.example.project.core.utils.Location

class MapDensityTest {
    @Test fun tenThousandVendorsRemainRepresentedWithBoundedNativeMarkers() {
        val locations = List(10_000) { Location((it % 180).toDouble() - 89, (it * 17 % 360).toDouble() - 179) }
        for (zoom in listOf(0f, 10f, 20f)) {
            val clusters = clusterVendorLocations(locations, zoom)
            assertTrue(clusters.size <= 150)
            assertEquals(10_000, clusters.sumOf { it.count })
        }
    }
    @Test fun antimeridianDoesNotMoveMarkersToGreenwichAndInvalidFixesAreIgnored() {
        val clusters = clusterVendorLocations(listOf(Location(0.0, 179.9), Location(0.0, -179.9), Location(Double.NaN, 0.0)), 0f)
        assertEquals(2, clusters.size)
        assertTrue(clusters.all { kotlin.math.abs(it.location.longitude) > 179 })
    }
    @Test fun zoomSeparatesNearbyVendors() {
        val points = listOf(Location(-23.55, -46.63), Location(-23.551, -46.631))
        assertEquals(1, clusterVendorLocations(points, 0f).size)
        assertEquals(2, clusterVendorLocations(points, 20f).size)
    }
    @Test fun routeRenderingKeepsEndpointsAndUniqueOrderedSamples() {
        assertEquals(emptyList(), routeMarkerIndices(0))
        assertEquals(listOf(0, 1), routeMarkerIndices(2))
        val indices = routeMarkerIndices(2000)
        assertEquals(48, indices.size)
        assertEquals(0, indices.first()); assertEquals(1999, indices.last())
        assertEquals(indices.distinct().sorted(), indices)
    }
}
