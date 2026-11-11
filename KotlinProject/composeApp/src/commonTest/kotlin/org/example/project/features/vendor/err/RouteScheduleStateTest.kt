package org.example.project.features.vendor.err

import org.example.project.core.domain.models.RouteWeekday
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteScheduleStateTest {
    @Test
    fun defaultsToEveryDayRoute() {
        val state = RouteScheduleState()

        assertTrue(state.usesEveryDay)
        assertTrue(state.canStartRoute)
        assertEquals("Todos os dias", state.scheduleSummary)
        assertEquals(RouteWeekday.everyDay(), state.activeDays)
    }

    @Test
    fun allowsOneRouteToUseMultipleSpecificDays() {
        val state = RouteScheduleState()
            .selectSpecificDays()
            .toggleDay(RouteWeekday.Monday)
            .toggleDay(RouteWeekday.Wednesday)
            .toggleDay(RouteWeekday.Friday)

        assertFalse(state.usesEveryDay)
        assertTrue(state.canStartRoute)
        assertEquals("Seg, Qua, Sex", state.scheduleSummary)
        assertEquals(
            listOf(RouteWeekday.Monday, RouteWeekday.Wednesday, RouteWeekday.Friday),
            state.activeDays
        )
    }

    @Test
    fun requiresAtLeastOneSpecificDay() {
        val state = RouteScheduleState().selectSpecificDays()

        assertFalse(state.canStartRoute)
        assertEquals("Escolha pelo menos um dia", state.scheduleSummary)
        assertEquals(emptyList(), state.activeDays)
    }

    @Test
    fun trimsRouteNameOrUsesFallback() {
        assertEquals("Centro", RouteScheduleState(routeName = "  Centro  ").routeNameForSave)
        assertEquals("Rota sem nome", RouteScheduleState(routeName = "   ").routeNameForSave)
    }
}
