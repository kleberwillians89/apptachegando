package org.example.project.features.consumer

import kotlin.test.*
import org.example.project.core.data.Vendor
import org.example.project.core.utils.Location
import org.example.project.core.utils.UiState

class VendorTrackingStateTest {
    private fun vendor() = Vendor("vendor", "Profissional", "", "Pães", "", "0.0", 0, true, -23.5, -46.6)
    @Test fun failedOrLoadingFeedNeverUsesThePreviousSuccessfulPosition() {
        assertEquals(Location(-23.5, -46.6), trackingLocation(UiState.Success(listOf(vendor()))))
        assertNull(trackingLocation(UiState.Error("offline")))
        assertNull(trackingLocation(UiState.Loading))
    }
    @Test fun offlineExpiredAndInvalidPositionsDoNotProduceAMarker() {
        for (v in listOf(vendor().copy(isOnline = false), vendor().copy(latitude = null),
            vendor().copy(latitude = Double.NaN), vendor().copy(longitude = 181.0))) {
            assertNull(trackingLocation(UiState.Success(listOf(v))))
        }
    }
    @Test fun missingOrAmbiguousVendorIdentityDoesNotProduceAMarker() {
        assertNull(trackingLocation(UiState.Success(emptyList())))
        assertNull(trackingLocation(UiState.Success(listOf(vendor(), vendor().copy(id = "other")))))
    }
}
