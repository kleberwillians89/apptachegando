package org.example.project.core.ui.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

import org.example.project.core.utils.Location

import org.example.project.core.ui.maps.HeatZoneLocation

@Composable
actual fun NativeMap(
    modifier: Modifier,
    userLocation: Location?,
    vendorLocations: List<Location>,
    routePoints: List<Location>,
    heatZones: List<HeatZoneLocation>,
    onMapClick: (Location) -> Unit,
    vendorMarkers: List<VendorMapMarker>,
    onVendorClick: (String) -> Unit
) {
    // Placeholder para WasmJS até a integração interop JS do Google Maps.
    Box(
        modifier = modifier.background(Color.LightGray),
        contentAlignment = Alignment.Center
    ) {
        Text("Google Maps Web (Placeholder)")
    }
}
