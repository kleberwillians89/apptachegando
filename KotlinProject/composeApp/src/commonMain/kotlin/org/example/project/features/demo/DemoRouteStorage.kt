package org.example.project.features.demo

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.project.core.domain.models.VendorRoute
import org.example.project.core.domain.route.RouteProgress

/** Only synthetic route geometry. No users, interests, credentials or device GPS. */
interface DemoRouteStorage {
    suspend fun read(): String?
    suspend fun write(value: String)
}

@Composable
expect fun rememberDemoRouteStorage(): DemoRouteStorage?

@Serializable
private data class DemoRouteArchive(val version: Int = 1, val routes: List<VendorRoute>)

internal fun validateDemoRoutes(routes: List<VendorRoute>) {
    require(routes.size <= 20 && routes.map { it.id }.distinct().size == routes.size)
    routes.forEach {
        require(it.id.matches(Regex("demo-route-[0-9]+")) && it.vendorId == "demo-route-owner" && !it.isActive)
        require(it.name.length in 1..80 && it.influencePolygon.isEmpty())
        RouteProgress(it.waypoints)
    }
}
internal fun encodeDemoRoutes(routes: List<VendorRoute>): String {
    validateDemoRoutes(routes)
    return Json.encodeToString(DemoRouteArchive(routes = routes)).also { require(it.length <= 2_000_000) }
}
internal fun decodeDemoRoutes(value: String): List<VendorRoute> {
    require(value.length <= 2_000_000)
    val archive = Json.decodeFromString<DemoRouteArchive>(value)
    require(archive.version == 1) { "Formato da rota demo não suportado; arquivo preservado." }
    return archive.routes.also(::validateDemoRoutes)
}
