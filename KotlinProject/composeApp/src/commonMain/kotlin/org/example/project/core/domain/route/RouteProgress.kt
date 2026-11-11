package org.example.project.core.domain.route

import kotlin.math.*
import org.example.project.core.domain.models.RouteWaypoint
import org.example.project.core.utils.GeoUtils
import org.example.project.core.utils.Location

/** Urban polyline projection in metres. Every segment, clamped endpoints, cumulative distance.
 * Prepared once per journey. This is not road routing, an ETA or a validated GPS map matcher. */
class RouteProgress(waypoints: List<RouteWaypoint>) {
    private data class XY(val x: Double, val y: Double)
    data class Projection(val alongMeters: Double, val crossTrackMeters: Double, val ambiguous: Boolean)
    private val points = waypoints.sortedBy { it.orderIndex }.map { Location(it.latitude, it.longitude) }
    private val origin = points.firstOrNull() ?: error("A rota precisa de pontos.")
    private val lonScale = 111_195.0 * cos(origin.latitude * PI / 180)
    private fun xy(p: Location) = XY((p.longitude - origin.longitude) * lonScale, (p.latitude - origin.latitude) * 111_195.0)
    private val plane: List<XY>
    private val prefix: DoubleArray
    val lengthMeters: Double get() = prefix.last()
    init {
        require(points.size in 2..2000 && waypoints.map { it.orderIndex }.distinct().size == points.size)
        require(points.all { valid(it) && GeoUtils.calculateDistanceMeters(origin, it) < 50_000 }) { "Rota urbana inválida ou muito extensa." }
        plane = points.map(::xy)
        prefix = DoubleArray(points.size)
        for (i in 1 until plane.size) prefix[i] = prefix[i - 1] + hypot(plane[i].x - plane[i - 1].x, plane[i].y - plane[i - 1].y)
        require(lengthMeters > 1) { "A rota precisa de pontos distintos." }
    }
    fun project(point: Location): Projection? {
        if (!valid(point)) return null
        val p = xy(point)
        val candidates = ArrayList<Projection>(plane.size - 1)
        for (i in 0 until plane.lastIndex) {
            val a = plane[i]; val b = plane[i + 1]
            val dx = b.x - a.x; val dy = b.y - a.y; val squared = dx * dx + dy * dy
            if (squared < 0.0001) continue
            val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / squared).coerceIn(0.0, 1.0)
            candidates += Projection(prefix[i] + t * sqrt(squared), hypot(p.x - a.x - t * dx, p.y - a.y - t * dy), false)
        }
        val best = candidates.minBy { it.crossTrackMeters }
        // Intersections / overlapping stretches cannot be disambiguated from one position.
        val tied = candidates.filter { it.crossTrackMeters <= best.crossTrackMeters + 2.0 }
        return best.copy(ambiguous = tied.maxOf { it.alongMeters } - tied.minOf { it.alongMeters } > 30.0)
    }
    fun locationAt(meters: Double): Location {
        val distance = meters.coerceIn(0.0, lengthMeters)
        val i = (0 until points.lastIndex).firstOrNull { prefix[it + 1] >= distance && prefix[it + 1] > prefix[it] } ?: points.lastIndex - 1
        val span = prefix[i + 1] - prefix[i]
        val t = if (span == 0.0) 0.0 else (distance - prefix[i]) / span
        return Location(points[i].latitude + t * (points[i + 1].latitude - points[i].latitude),
            points[i].longitude + t * (points[i + 1].longitude - points[i].longitude))
    }
    companion object {
        fun valid(p: Location) = p.latitude.isFinite() && p.longitude.isFinite() && p.latitude in -90.0..90.0 && p.longitude in -180.0..180.0
    }
}
