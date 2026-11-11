package org.example.project.core.ui.maps

internal enum class MapLoadStatus { Unavailable, Offline, Loading, TimedOut, Ready }

/** Network availability does not prove tiles loaded; only the SDK callback does. */
internal fun mapLoadStatus(configured: Boolean, online: Boolean, loaded: Boolean, timedOut: Boolean): MapLoadStatus = when {
    !configured -> MapLoadStatus.Unavailable
    !online -> MapLoadStatus.Offline
    loaded -> MapLoadStatus.Ready
    timedOut -> MapLoadStatus.TimedOut
    else -> MapLoadStatus.Loading
}
