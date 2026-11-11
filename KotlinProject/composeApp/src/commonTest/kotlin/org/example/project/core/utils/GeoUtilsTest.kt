package org.example.project.core.utils

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoUtilsTest {
    @Test fun distanceUsesMultiplatformRadians() {
        assertEquals(0.0, GeoUtils.calculateDistanceMeters(Location(0.0,0.0), Location(0.0,0.0)))
        assertTrue(abs(GeoUtils.calculateDistanceMeters(Location(0.0,0.0), Location(0.0,1.0))-111195.0)<2)
        assertTrue(GeoUtils.calculateDistanceMeters(Location(-23.5505,-46.6333),Location(-23.5506,-46.6333))>10)
    }
}
