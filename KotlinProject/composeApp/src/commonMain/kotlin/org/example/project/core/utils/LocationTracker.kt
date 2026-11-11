package org.example.project.core.utils

import androidx.compose.runtime.Composable

interface LocationTracker {
    suspend fun requestLocationPermission(): Boolean = true
    suspend fun getCurrentLocation(): Location?
    fun startTracking(onLocationUpdate: (Location) -> Unit)
    // Unknown platform accuracy is explicitly rejected by the attendance reducer.
    fun startTrackingFix(onFix: (org.example.project.core.domain.route.RouteGpsFix) -> Unit) {
        startTracking { onFix(org.example.project.core.domain.route.RouteGpsFix(it, Double.NaN, kotlinx.datetime.Clock.System.now().toEpochMilliseconds())) }
    }
    fun stopTracking()
}

@Composable
expect fun rememberLocationTracker(): LocationTracker
