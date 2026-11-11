package org.example.project.core.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.example.project.features.demo.DemoConsumerExperience
import org.example.project.core.ui.maps.clusterVendorLocations
import org.example.project.core.utils.Location
import org.example.project.core.utils.UiState

class DemoExperienceTest {
    @Test fun fiveFictionalVendorsHaveTenGenericCategoriesWithoutPricesAndValidMapCoordinates() {
        val demo = DemoConsumerExperience()
        assertTrue(demo.isDemonstration)
        assertEquals(5, demo.catalogue.size)
        assertEquals(5, demo.catalogue.map { it.id }.distinct().size)
        assertTrue(demo.catalogue.all { it.name.startsWith("[DEMO]") && it.isOnline && it.products.isNotEmpty() })
        assertEquals(listOf("[DEMO] Pão do José", "[DEMO] Frutas do Luiz", "[DEMO] Salgados da Praça", "[DEMO] Lanches Naturais", "[DEMO] Sorvetes do Bairro"), demo.catalogue.map { it.name })
        val products = demo.catalogue.flatMap { it.products }
        assertEquals(10, products.size)
        assertTrue(products.all { it.name in org.example.project.features.demo.demoCategories })
        assertEquals(products.size, products.map { it.id }.distinct().size)
        assertTrue(products.all { it.id.startsWith("demo-product-") && it.price.isEmpty() })
        for (id in demo.catalogue.map { it.id } + products.map { it.id }) {
            kotlin.test.assertNotNull(org.example.project.features.demo.demoPhotoResource(id), "Missing local photo: $id")
        }
        val points = demo.catalogue.map { Location(it.latitude!!, it.longitude!!) }
        assertEquals(5, clusterVendorLocations(points, 15f).sumOf { it.count })
    }
    @Test fun demoCatalogueAndUnknownIdsNeverFallBackToTheRealBackend() = runTest {
        val demo = DemoConsumerExperience()
        assertEquals(5, assertIs<UiState.Success<List<Vendor>>>(demo.vendors().first()).data.size)
        assertTrue(assertIs<UiState.Success<List<Vendor>>>(demo.vendors(page = 1).first()).data.isEmpty())
        assertTrue(assertIs<UiState.Success<List<Vendor>>>(demo.vendors(vendorId = "real-unknown-id").first()).data.isEmpty())
        assertIs<UiState.Error>(demo.createIntent("real-unknown-id", demo.referenceLocation))
        assertIs<UiState.Error>(demo.pendingIntent("real-unknown-id"))
    }
    @Test fun localIntentIsDeduplicatedAndCanBeConsultedWithoutAnAuthSession() = runTest {
        val demo = DemoConsumerExperience(); val id = demo.catalogue.first().id
        assertNull(assertIs<UiState.Success<PurchaseIntentReceipt?>>(demo.pendingIntent(id)).data)
        val first = assertIs<UiState.Success<PurchaseIntentReceipt>>(demo.createIntent(id, demo.referenceLocation)).data
        val again = assertIs<UiState.Success<PurchaseIntentReceipt>>(demo.createIntent(id, demo.referenceLocation)).data
        assertEquals(first, again);assertEquals("simulated", first.status);assertTrue(first.id.startsWith("DEMO-"))
        assertEquals(first, assertIs<UiState.Success<PurchaseIntentReceipt?>>(demo.pendingIntent(id)).data)
    }
    @Test fun leavingAndReenteringDemoDoesNotRetainOrLeakIntentState() = runTest {
        val a = DemoConsumerExperience(); val id = a.catalogue.first().id
        a.createIntent(id, a.referenceLocation)
        assertNull(assertIs<UiState.Success<PurchaseIntentReceipt?>>(DemoConsumerExperience().pendingIntent(id)).data)
        assertNull(assertIs<UiState.Success<PurchaseIntentReceipt?>>(a.pendingIntent(a.catalogue[1].id)).data)
    }
    @Test fun remoteSourceRejectsDemoIdsBeforeTouchingAuthOrNetwork() = runTest {
        val id = DemoConsumerExperience().catalogue.first().id
        assertFailsWith<IllegalArgumentException> { RemoteConsumerExperience.vendors(vendorId = id) }
        assertFailsWith<IllegalArgumentException> { RemoteConsumerExperience.pendingIntent(id) }
        assertFailsWith<IllegalArgumentException> { RemoteConsumerExperience.createIntent(id, Location(0.0, 0.0)) }
    }

}
