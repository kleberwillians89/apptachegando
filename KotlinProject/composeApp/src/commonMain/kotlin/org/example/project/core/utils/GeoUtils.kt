package org.example.project.core.utils

import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable
data class Location(val latitude: Double, val longitude: Double)

object GeoUtils {
    private fun toRadians(degrees: Double): Double = degrees * PI / 180.0

    private const val EARTH_RADIUS_KM = 6371.0

    fun calculateDistanceMeters(loc1: Location, loc2: Location): Double {
        val dLat = toRadians(loc2.latitude - loc1.latitude)
        val dLon = toRadians(loc2.longitude - loc1.longitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(toRadians(loc1.latitude)) * cos(toRadians(loc2.latitude)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c * 1000.0
    }

    fun calculatePerpendicularDistance(point: Location, lineStart: Location, lineEnd: Location): Double {
        // Simple 2D cross product approximation for small distances
        val dx = lineEnd.longitude - lineStart.longitude
        val dy = lineEnd.latitude - lineStart.latitude

        val num = abs(dy * point.longitude - dx * point.latitude + lineEnd.longitude * lineStart.latitude - lineEnd.latitude * lineStart.longitude)
        val den = sqrt(dx * dx + dy * dy)

        return if (den == 0.0) 0.0 else num / den * 111000.0 // approx meters
    }
}
