package org.example.project.core.performance

import org.example.project.core.ui.maps.*
import kotlin.test.*

class MapLoadStatusTest {
    @Test fun internetAloneDoesNotMeanMapLoaded() {
        assertEquals(MapLoadStatus.Loading, mapLoadStatus(true, true, false, false))
        assertEquals(MapLoadStatus.TimedOut, mapLoadStatus(true, true, false, true))
        assertEquals(MapLoadStatus.Ready, mapLoadStatus(true, true, true, true))
    }
    @Test fun offlineIsVisibleEvenAfterTilesPreviouslyLoaded() {
        assertEquals(MapLoadStatus.Offline, mapLoadStatus(true, false, true, false))
        assertEquals(MapLoadStatus.Unavailable, mapLoadStatus(false, true, true, false))
        assertEquals(MapLoadStatus.Loading, mapLoadStatus(true, true, false, false))
    }
}
