package org.example.project.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*
import org.example.project.core.domain.models.*
import org.example.project.core.utils.*

class CanonicalContractsTest {
    @Test fun decimalPricesKeepCentsWithoutFloatingPoint() {
        assertEquals("12.50", canonicalPrice("12,5"))
        assertEquals("0.01", canonicalPrice("0.01"))
        assertEquals("99999999.99", canonicalPrice("99999999.99"))
        for (input in listOf("-1", "NaN", "1.234", "1e2", "100000000", "")) {
            assertFailsWith<IllegalArgumentException> { canonicalPrice(input) }
        }
    }
    @Test fun normalizedProductsDecodeNumericPrices() {
        val row = Json.decodeFromString<ProductRecord>("""{"id":"product","name":"Pão","price":12.50}""")
        assertEquals(Product("product", "Pão", "12.50"), row.toDomain())
    }
    @Test fun routeProjectionDecodesServerPolygonAndSchedule() {
        val record = Json.decodeFromString<RouteRecord>("""{
            "id":"route","vendor_id":"vendor","name":"Circuito",
            "waypoints":[{"latitude":-23.0,"longitude":-46.0,"orderIndex":0}],
            "active_days":["monday","saturday"],"deviation_tolerance_meters":150,"status":"active",
            "polygon":[[[-46.0,-23.0],[-46.1,-23.0],[-46.1,-23.1],[-46.0,-23.0]]]
        }""")
        val route = record.toDomain()
        assertEquals(Location(-23.0, -46.0), route.influencePolygon.first())
        assertEquals(listOf(RouteWeekday.Monday, RouteWeekday.Saturday), route.activeDays)
        assertTrue(route.isActive)
    }
    @Test fun insertFailureIsNeverAnOfflineSuccess() = runTest {
        val repository = RouteRepository(FakeRoutes(failInsert = true))
        assertFailsWith<IllegalStateException> { repository.saveRoute(route()) }
    }
    @Test fun freshRepositoryLoadsPersistedRouteInsteadOfProcessCache() = runTest {
        val source = FakeRoutes()
        val savedId = RouteRepository(source).saveRoute(route())
        val loaded = RouteRepository(source).getRoute(savedId)
        assertEquals(savedId, loaded?.id)
        assertEquals(savedId, RouteRepository(source).getActiveRoute("vendor")?.id)
        assertEquals(route().waypoints, loaded?.waypoints)
        RouteRepository(source).endRoute(savedId)
        assertFalse(RouteRepository(source).getRoute(savedId)!!.isActive)
        val reusable = RouteRepository(source).getSavedRoutes("vendor").single()
        assertEquals(route().waypoints, reusable.waypoints)
        assertEquals(route().name, reusable.name)
        assertEquals(route().activeDays, reusable.activeDays)
        assertTrue(RouteRepository(source).getSavedRoutes("other-vendor").isEmpty())
    }
    @Test fun invalidRouteNeverReachesTransport() = runTest {
        // This source fails differently if the insert is reached.
        val repository = RouteRepository(FakeRoutes(failInsert = true))
        for (invalid in listOf(
            route().copy(waypoints = emptyList()),
            route().copy(waypoints = listOf(RouteWaypoint(91.0, 0.0, 0), RouteWaypoint(1.0, 1.0, 1))),
            route().copy(waypoints = listOf(RouteWaypoint(1.0, 1.0, 0), RouteWaypoint(1.0, 1.0, 1))),
            route().copy(name = " "),
            route().copy(activeDays = emptyList())
        )) assertFailsWith<IllegalArgumentException> { repository.saveRoute(invalid) }
    }

    @Test fun activeJourneyBlocksAnotherRouteBeforeBufferOrInsert() = runTest {
        val source = FakeRoutes()
        RouteRepository(source).saveRoute(route())
        val engine = org.example.project.features.vendor.err.ERREngine(RouteRepository(source))
        val failure = assertFailsWith<IllegalStateException> {
            engine.initRoute("vendor", route().waypoints)
        }
        assertEquals("Encerre a rota ativa antes de iniciar outra.", failure.message)
    }
    @Test fun backendFailureIsVisibleAndCancellationPropagates() = runTest {
        assertEquals(UiState.Error("Unavailable"), backendResult("Unavailable") { error("network") })
        assertFailsWith<CancellationException> { backendResult("Unavailable") { throw CancellationException("stop") } }
    }
    @Test fun bufferFailureDoesNotInventRectangle() = runTest {
        assertFailsWith<IllegalStateException> { RouteRepository(FakeRoutes()).generateBufferPolygon(route().waypoints, 150) }
    }
    private fun route() = VendorRoute("", "vendor", "Rota", listOf(RouteWaypoint(-23.0,-46.0,0), RouteWaypoint(-23.1,-46.1,1)), emptyList(), isActive=true)
    private class FakeRoutes(val failInsert: Boolean = false) : RouteDataSource {
        private var saved: VendorRoute? = null
        override suspend fun buffer(waypoints: List<RouteWaypoint>, toleranceMeters: Int): List<Location> = error("RPC failed")
        override suspend fun insert(route: VendorRoute): String {
            if (failInsert) error("Database rejected insert")
            saved = route.copy(id="server-route-id")
            return saved!!.id
        }
        override suspend fun active(vendorId: String): VendorRoute? = saved?.takeIf { it.vendorId == vendorId && it.isActive }
        override suspend fun saved(vendorId: String): List<VendorRoute> = listOfNotNull(saved?.takeIf { it.vendorId == vendorId && !it.isActive })
        override suspend fun load(routeId: String): VendorRoute? = saved?.takeIf { it.id == routeId }
        override suspend fun intents(routeId: String): List<PurchaseIntent> = emptyList()
        override suspend fun end(routeId: String) { saved = saved!!.copy(isActive=false) }
    }
}
