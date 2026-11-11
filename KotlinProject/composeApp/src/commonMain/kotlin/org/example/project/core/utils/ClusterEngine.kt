package org.example.project.core.utils

import org.example.project.core.domain.models.*

class ClusterEngine(private val config: ERRConfig) {

    fun generateClusters(
        intents: List<PurchaseIntent>,
        routeWaypoints: List<RouteWaypoint>
    ): List<DemandCluster> {
        if (intents.isEmpty()) return emptyList()

        // Mock DBSCAN grouping by simple proximity for demonstration
        val clusters = intents.groupBy {
            // grouping by truncated lat/lng to simulate epsilon clustering
            "${(it.location.latitude * 100).toInt()}_${(it.location.longitude * 100).toInt()}"
        }.map { (key, group) ->
            val centroidLat = group.sumOf { it.location.latitude } / group.size
            val centroidLon = group.sumOf { it.location.longitude } / group.size
            val centroid = Location(centroidLat, centroidLon)

            // Calculate distance to closest route segment
            val dist = if (routeWaypoints.size >= 2) {
                GeoUtils.calculatePerpendicularDistance(
                    centroid,
                    Location(routeWaypoints[0].latitude, routeWaypoints[0].longitude),
                    Location(routeWaypoints.last().latitude, routeWaypoints.last().longitude)
                )
            } else {
                0.0
            }

            val detourSecs = (dist / 5.0).toInt() // 5 m/s approx
            val score = calculateViabilityScore(group.size, dist)

            DemandCluster(
                id = key,
                centroid = centroid,
                intentCount = group.size,
                intents = group,
                perpendicularDistanceMeters = dist,
                estimatedDetourSeconds = detourSecs,
                viabilityScore = score
            )
        }

        return clusters.filter { it.viabilityScore >= config.minViabilityScore }
    }

    private fun calculateViabilityScore(volume: Int, distanceMeters: Double): Double {
        if (distanceMeters <= 0) return 1.0
        return (volume * config.weightVolume) / (distanceMeters * config.weightDistance)
    }
}
