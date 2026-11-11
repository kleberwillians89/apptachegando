package org.example.project.core.data

import kotlin.test.*
import org.example.project.features.demo.*

class DemoPresentationTest {
    @Test fun registrationSupportsBothPersonasAndRemainsInTheDemoInstance() {
        val demo = DemoConsumerExperience()
        assertNull(demo.registration)
        assertEquals(DemoRole.Consumer, demo.register(" Ana Demo ", DemoRole.Consumer).role)
        assertEquals("Ana Demo", demo.registration?.name)
        assertEquals(DemoRole.Vendor, demo.register("Vendedor Demo", DemoRole.Vendor).role)
        assertEquals("Pães", demo.registration?.category)
        assertEquals("Frutas", demo.register("Luiz Demo", DemoRole.Vendor, "Frutas").category)
        assertEquals("demo-vendor-frutas", demo.ownedVendorId)
        assertFailsWith<IllegalArgumentException> { demo.register("Demo", DemoRole.Vendor, "Inexistente") }
        assertNull(DemoConsumerExperience().registration)
        assertFailsWith<IllegalArgumentException> { demo.register(" ", DemoRole.Consumer) }
        assertFailsWith<IllegalArgumentException> { demo.register("a".repeat(41), DemoRole.Vendor) }
    }
    @Test fun searchMatchesVendorsCategoriesAndProductsWithOrWithoutAccents() {
        val vendors = DemoConsumerExperience().catalogue
        for (query in listOf("pao do jose", "Pães", "paes")) assertEquals("demo-vendor-paes", demoSearch(vendors, query).single().id)
        assertEquals("demo-vendor-frutas", demoSearch(vendors, "FRUTAS").single().id)
        assertEquals("demo-vendor-lanches", demoSearch(vendors, "lanches naturais").single().id)
        assertEquals("demo-vendor-frutas", demoSearch(vendors, "temperos").single().id)
        assertTrue(demoSearch(vendors, "produto inexistente").isEmpty())
        assertEquals(5, demoSearch(vendors, " ").size)
    }
    @Test fun nearbyUsesSyntheticReferenceAndIgnoresOfflineMissingAndDistantPositions() {
        val demo = DemoConsumerExperience(); val near = demo.catalogue.first()
        val offline = near.copy(id = "offline", isOnline = false)
        val missing = near.copy(id = "missing", latitude = null)
        val far = near.copy(id = "far", latitude = 0.0, longitude = 0.0)
        assertEquals(listOf(near), demoNearby(listOf(near, offline, missing, far), demo.referenceLocation))
        assertEquals(5, demoNearby(demo.catalogue, demo.referenceLocation).size)
    }
    @Test fun interestHasRetrievableDeduplicatedNonCommercialProtocol() {
        val demo = DemoConsumerExperience(); val vendor = demo.catalogue.first(); val category = vendor.products.first()
        val interest = demo.consultAvailability(vendor.id, category.id)
        assertEquals("DEMO-INTERESSE-0001", interest.protocol)
        assertEquals("Pães", interest.productName)
        assertEquals(interest, demo.interest(interest.protocol))
        assertEquals(interest, demo.consultAvailability(vendor.id, category.id))
        assertEquals(1, demo.interestHistory().size)
        val other = demo.consultAvailability(vendor.id, vendor.products.last().id)
        assertNotEquals(interest.protocol, other.protocol)
        assertEquals(other, demo.interestHistory().first())
        assertNull(DemoConsumerExperience().interest(interest.protocol))
    }
    @Test fun availabilityRejectsForeignProductsRealIdsAndOfflineSellers() {
        val demo = DemoConsumerExperience(); val a = demo.catalogue[0]; val b = demo.catalogue[1]
        assertFailsWith<NoSuchElementException> { demo.consultAvailability(a.id, b.products.first().id) }
        assertFailsWith<NoSuchElementException> { demo.consultAvailability("real-vendor-id", a.products.first().id) }
        demo.register("Vendedor demo", DemoRole.Vendor)
        assertFailsWith<NoSuchElementException> { demo.consultAvailability(a.id, a.products.first().id) }
        assertTrue(demo.interestHistory().isEmpty())
    }
}
