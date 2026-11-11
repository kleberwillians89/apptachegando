package org.example.project.core.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

class WasmLocationTracker : LocationTracker {
    override suspend fun getCurrentLocation(): Location? {
        return null
    }

    override fun startTracking(onLocationUpdate: (Location) -> Unit) {
    }

    override fun stopTracking() {
    }
}

@Composable
actual fun rememberLocationTracker(): LocationTracker {
    return remember { WasmLocationTracker() }
}
