package org.example.project.features.demo

import org.example.project.core.data.Vendor
import org.example.project.core.utils.GeoUtils
import org.example.project.core.utils.Location

/** All presentation records exist only in the current demonstration instance. */
enum class DemoRole { Consumer, Vendor }
val demoCategories = listOf("Pães", "Frutas", "Salgados fritos", "Salgados assados", "Doces de padaria", "Lanches naturais", "Sorvetes", "Picolés", "Temperos", "Produtos de limpeza prontos")
data class DemoRegistration(val name: String, val role: DemoRole, val category: String? = null)
/** A contact interest, never a commercial order or a stock reservation. */
data class DemoInterest(
    val protocol: String, val vendorId: String, val productId: String,
    val vendorName: String, val productName: String
)

private fun normalized(value: String): String = buildString {
    for (c in value.lowercase()) append(when (c) {
        'á','à','â','ã','ä' -> 'a'; 'é','è','ê','ë' -> 'e'; 'í','ì','î','ï' -> 'i'
        'ó','ò','ô','õ','ö' -> 'o'; 'ú','ù','û','ü' -> 'u'; 'ç' -> 'c'; else -> c
    })
}.trim()

fun demoSearch(vendors: List<Vendor>, query: String): List<Vendor> {
    val needle = normalized(query)
    if (needle.isEmpty()) return vendors
    return vendors.filter { vendor ->
        normalized(vendor.name).contains(needle) || normalized(vendor.category).contains(needle) ||
            vendor.products.any { normalized(it.name).contains(needle) }
    }
}

fun demoNearby(vendors: List<Vendor>, reference: Location, radiusMeters: Double = 1_000.0): List<Vendor> {
    require(radiusMeters.isFinite() && radiusMeters > 0)
    return vendors.mapNotNull { vendor ->
        if (!vendor.isOnline || vendor.latitude == null || vendor.longitude == null) null else {
            val distance = GeoUtils.calculateDistanceMeters(reference, Location(vendor.latitude, vendor.longitude))
            if (!distance.isFinite() || distance > radiusMeters) null else vendor to distance
        }
    }.sortedBy { it.second }.map { it.first }
}

/** Vendor-side states are local presentation states, never backend status claims. */
enum class DemoIntentStatus { Pending, Accepted, Completed }
data class DemoIncomingIntent(val interest: DemoInterest, val status: DemoIntentStatus)
