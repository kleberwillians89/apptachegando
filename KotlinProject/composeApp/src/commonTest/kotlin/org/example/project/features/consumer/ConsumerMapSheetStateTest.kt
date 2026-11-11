package org.example.project.features.consumer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConsumerMapSheetStateTest {
    @Test
    fun togglesNearbyProfessionalsSheet() {
        val visible = ConsumerMapSheetState()

        val hidden = visible.hide()

        assertFalse(hidden.isVisible)
        assertTrue(hidden.show().isVisible)
    }

    @Test
    fun settlesSheetAfterVerticalDrag() {
        assertFalse(ConsumerMapSheetState().settleExpandedDrag(120f).isVisible)
        assertTrue(ConsumerMapSheetState(isVisible = false).settleCollapsedDrag(-120f).isVisible)
    }
}
