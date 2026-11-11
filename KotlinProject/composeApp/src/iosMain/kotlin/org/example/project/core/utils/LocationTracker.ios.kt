package org.example.project.core.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

class IosLocationTracker : LocationTracker {
    override suspend fun getCurrentLocation(): Location? {
        // TODO: Implement with CLLocationManager
        return null
    }

    override fun startTracking(onLocationUpdate: (Location) -> Unit) {
        // TODO: Implement
    }

    override fun stopTracking() {
        // TODO: Implement
    }
}

@Composable
actual fun rememberLocationTracker(): LocationTracker {
    return remember { IosLocationTracker() }
}
