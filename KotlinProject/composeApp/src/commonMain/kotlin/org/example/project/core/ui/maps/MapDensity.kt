package org.example.project.core.ui.maps

import kotlin.math.floor
import kotlin.math.pow
import org.example.project.core.utils.Location

internal data class VendorMapCluster(val key: String, val location: Location, val count: Int, val sourceIndices: List<Int>)

/** Bounded grid, no extra SDK. Every valid location contributes to exactly one marker.
 * Cells do not cross the antimeridian. Zooming in splits cells; dense views stay <=150 markers.
 */
internal fun clusterVendorLocations(locations: List<Location>, zoom: Float): List<VendorMapCluster> {
    val valid = locations.withIndex().filter { (_, point) -> point.latitude.isFinite() && point.longitude.isFinite() &&
        point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0 }
    var columns = 2.0.pow(zoom.coerceIn(0f, 20f).toInt() + 2).toInt().coerceIn(8, 4_194_304)
    while (true) {
        val rows = columns / 2
        val groups = valid.groupBy { (_, point) ->
            val x = floor((point.longitude + 180) / 360 * columns).toInt().coerceIn(0, columns - 1)
            val y = floor((point.latitude + 90) / 180 * rows).toInt().coerceIn(0, rows - 1)
            x to y
        }
        if (groups.size <= 150 || columns == 8) {
            return groups.map { (cell, points) ->
                VendorMapCluster("$columns:${cell.first}:${cell.second}",
                    Location(points.sumOf { it.value.latitude } / points.size, points.sumOf { it.value.longitude } / points.size),
                    points.size, points.map { it.index })
            }
        }
        columns = (columns / 2).coerceAtLeast(8)
    }
}

/** Keep the full polyline, but cap native marker objects for dense route recordings. */
internal fun routeMarkerIndices(size: Int): List<Int> {
    require(size >= 0)
    if (size <= 48) return (0 until size).toList()
    return (0 until 48).map { (it.toLong() * (size - 1) / 47).toInt() }
}
