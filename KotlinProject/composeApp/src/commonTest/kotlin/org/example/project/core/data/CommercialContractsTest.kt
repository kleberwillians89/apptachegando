package org.example.project.core.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.example.project.core.ui.maps.clusterVendorLocations
import org.example.project.core.utils.Location

class CommercialContractsTest {
    @Test fun pricesKeepCentsWithoutInventingFreeProductsForMissingData() {
        assertEquals("R$ 12,50", productDisplayPrice("12.50"))
        assertEquals("R$ 0,01", productDisplayPrice("0.01"))
        assertEquals("R$ 99999999,99", productDisplayPrice("99999999.99"))
        for (value in listOf("", "NaN", "-1", "1.999")) assertEquals("Preço indisponível", productDisplayPrice(value))
    }

    @Test fun filteredAndOverlappingMarkersKeepTheirOriginalSellerIdentity() {
        val points = listOf(Location(Double.NaN, 0.0), Location(-23.55, -46.63), Location(-23.55, -46.63), Location(10.0, 10.0))
        val groups = clusterVendorLocations(points, 20f)
        assertEquals(listOf(1, 2, 3), groups.flatMap { it.sourceIndices }.sorted())
        assertEquals(listOf(1, 2), groups.single { it.count == 2 }.sourceIndices)
        assertEquals(listOf(3), groups.single { it.count == 1 }.sourceIndices)
    }

    @Test fun persistedIntentReceiptRequiresIdentityStatusAndExpiryFromCanonicalColumns() {
        val row = Json.decodeFromString<PurchaseIntentReceipt>("""{"id":"intent-id","vendor_id":"vendor-id","status":"pending","expires_at":"2026-11-03T15:30:00Z"}""")
        assertEquals("vendor-id", row.vendorId)
        assertEquals("pending", row.status)
        assertEquals("2026-11-03T15:30:00Z", row.expiresAt)
        assertFailsWith<kotlinx.serialization.SerializationException> {
            Json.decodeFromString<PurchaseIntentReceipt>("""{"id":"intent-id"}""")
        }
    }
}
