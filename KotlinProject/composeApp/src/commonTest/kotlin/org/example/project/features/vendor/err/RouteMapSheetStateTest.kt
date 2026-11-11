package org.example.project.features.vendor.err

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteMapSheetStateTest {
    @Test
    fun togglesRouteMenusOverMap() {
        val visible = RouteMapSheetState()

        val hidden = visible.hide()

        assertFalse(hidden.isVisible)
        assertTrue(hidden.show().isVisible)
    }

    @Test
    fun settlesRouteMenusAfterVerticalDrag() {
        assertFalse(RouteMapSheetState().settleExpandedDrag(120f).isVisible)
        assertTrue(RouteMapSheetState(isVisible = false).settleCollapsedDrag(-120f).isVisible)
    }
}
