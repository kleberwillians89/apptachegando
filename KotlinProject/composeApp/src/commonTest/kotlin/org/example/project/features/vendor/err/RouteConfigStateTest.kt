package org.example.project.features.vendor.err

import kotlin.test.Test
import kotlin.test.assertEquals

class RouteConfigStateTest {
    @Test
    fun updatesDeviationToleranceFromAdjustAction() {
        val state = RouteConfigState()

        val adjusted = state.adjustTolerance(250)

        assertEquals(250, adjusted.deviationToleranceMeters)
        assertEquals("250 metros", adjusted.toleranceLabel)
        assertEquals(250, adjusted.toERRConfig().deviationToleranceMeters)
    }

    @Test
    fun usesFriendlyDistancePresetsForVendors() {
        val close = RouteConfigState().selectDistancePreset(RouteDistancePreset.Close)
        val normal = RouteConfigState().selectDistancePreset(RouteDistancePreset.Normal)
        val far = RouteConfigState().selectDistancePreset(RouteDistancePreset.Far)

        assertEquals(100, close.deviationToleranceMeters)
        assertEquals("Perto", close.distanceLabel)
        assertEquals(150, normal.deviationToleranceMeters)
        assertEquals("Normal", normal.distanceLabel)
        assertEquals(300, far.deviationToleranceMeters)
        assertEquals("Mais longe", far.distanceLabel)
    }
}
