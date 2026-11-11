package org.example.project.core.domain.models

import kotlinx.serialization.Serializable
import org.example.project.core.utils.Location

@Serializable
data class VendorRoute(
    val id: String,
    val vendorId: String,
    val name: String = "Rota sem nome",
    val waypoints: List<RouteWaypoint>,
    val influencePolygon: List<Location>,
    val deviationToleranceMeters: Int = 150,
    val activeDays: List<RouteWeekday> = RouteWeekday.everyDay(),
    val isActive: Boolean = false
)

@Serializable
data class RouteWaypoint(
    val latitude: Double,
    val longitude: Double,
    val orderIndex: Int
)

@Serializable
enum class RouteWeekday(
    val storageValue: String,
    val shortLabel: String,
    val label: String
) {
    Monday("monday", "Seg", "Segunda"),
    Tuesday("tuesday", "Ter", "Terca"),
    Wednesday("wednesday", "Qua", "Quarta"),
    Thursday("thursday", "Qui", "Quinta"),
    Friday("friday", "Sex", "Sexta"),
    Saturday("saturday", "Sab", "Sabado"),
    Sunday("sunday", "Dom", "Domingo");

    companion object {
        fun everyDay(): List<RouteWeekday> {
            return listOf(Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday)
        }
    }
}

@Serializable
data class ERRConfig(
    val deviationToleranceMeters: Int = 150,
    val clusterEpsilonMeters: Double = 100.0,
    val minViabilityScore: Double = 0.5,
    val weightVolume: Double = 1.0,
    val weightDistance: Double = 2.0
)

@Serializable
data class DemandCluster(
    val id: String,
    val centroid: Location,
    val intentCount: Int,
    val intents: List<PurchaseIntent>,
    val perpendicularDistanceMeters: Double,
    val estimatedDetourSeconds: Int,
    val viabilityScore: Double
)

@Serializable
data class PurchaseIntent(
    val id: String,
    val consumerId: String,
    val location: Location,
    val status: String = "pending"
)
