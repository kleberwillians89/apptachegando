package org.example.project.features.consumer

import org.example.project.core.data.Vendor
import org.example.project.core.utils.Location
import org.example.project.core.utils.UiState

/** An old successful read must never mask a failed/disconnected live position feed. */
internal fun trackingLocation(state: UiState<List<Vendor>>): Location? {
    val vendor = (state as? UiState.Success)?.data?.singleOrNull() ?: return null
    if (!vendor.isOnline) return null
    val lat = vendor.latitude ?: return null
    val lng = vendor.longitude ?: return null
    return if (lat.isFinite() && lat in -90.0..90.0 && lng.isFinite() && lng in -180.0..180.0) Location(lat, lng) else null
}
