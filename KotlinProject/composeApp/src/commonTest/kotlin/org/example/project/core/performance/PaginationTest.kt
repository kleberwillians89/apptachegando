package org.example.project.core.performance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.example.project.core.data.pageBounds

class PaginationTest {
    @Test fun pagesHaveNoGapsOrOverlappingRows() {
        assertEquals(0L..49L, pageBounds(0, 50))
        assertEquals(50L..99L, pageBounds(1, 50))
        assertEquals(107374182350L..107374182399L, pageBounds(Int.MAX_VALUE, 50))
    }
    @Test fun invalidRequestsFailBeforeNetwork() {
        assertFailsWith<IllegalArgumentException> { pageBounds(-1, 50) }
        assertFailsWith<IllegalArgumentException> { pageBounds(0, 0) }
        assertFailsWith<IllegalArgumentException> { pageBounds(0, 101) }
    }
}
