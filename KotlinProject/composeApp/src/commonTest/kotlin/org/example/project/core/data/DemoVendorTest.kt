package org.example.project.core.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.example.project.core.utils.UiState
import org.example.project.features.demo.*

class DemoVendorTest {
    private fun seller() = DemoConsumerExperience().apply { register("Beto Demo", DemoRole.Vendor) }
    @Test fun sellerStartsOfflineAndPublishesOnlyAnExplicitLocalPosition() = runTest {
        val demo = seller();val id=demo.ownedVendorId
        assertFalse(demo.ownVendor().isOnline);assertNull(demo.ownVendor().latitude)
        assertTrue(assertIs<UiState.Success<List<Vendor>>>(demo.vendors(vendorId=id).first()).data.isEmpty())
        assertFailsWith<IllegalStateException> { demo.publishDemoLocation() }
        demo.setDemoOnline(true);assertNull(demo.ownVendor().latitude)
        demo.publishDemoLocation();assertNotNull(demo.ownVendor().latitude)
        assertEquals(1,assertIs<UiState.Success<List<Vendor>>>(demo.vendors(vendorId=id).first()).data.size)
        demo.setDemoOnline(false);assertNull(demo.ownVendor().latitude);assertNull(demo.ownVendor().longitude)
        assertTrue(assertIs<UiState.Success<List<Vendor>>>(demo.vendors(vendorId=id).first()).data.isEmpty())
    }
    @Test fun friedSnackSellerKeepsItsOwnSyntheticPositionAndContactLifecycle() = runTest {
        val demo = DemoConsumerExperience()
        val original = demo.catalogue.single { it.id == "demo-vendor-salgados" }
        val bread = demo.catalogue.single { it.id == "demo-vendor-paes" }
        demo.register("Carlos da Coxinha", DemoRole.Vendor, "Salgados fritos")
        assertEquals(original.id, demo.ownedVendorId)
        assertFalse(demo.ownVendor().isOnline)
        assertNull(demo.ownVendor().latitude)
        demo.setDemoOnline(true); demo.publishDemoLocation()
        assertEquals(original.latitude, demo.ownVendor().latitude)
        assertEquals(original.longitude, demo.ownVendor().longitude)
        assertEquals(bread, demo.catalogue.single { it.id == bread.id })
        assertEquals(5, demo.catalogue.map { it.latitude to it.longitude }.distinct().size)
        val interest = demo.simulateCustomer()
        assertEquals("Salgados fritos", interest.productName)
        assertEquals(original.id, interest.vendorId)
        assertEquals(interest.protocol, demo.simulateCustomer().protocol)
        demo.advanceDemoIntent(interest.protocol)
        assertEquals(DemoIntentStatus.Accepted, demo.incomingIntents().single().status)
        demo.advanceDemoIntent(interest.protocol)
        assertEquals(DemoIntentStatus.Completed, demo.incomingIntents().single().status)
        demo.setDemoOnline(false)
        assertTrue(assertIs<UiState.Success<List<Vendor>>>(demo.vendors(vendorId = original.id).first()).data.isEmpty())
        assertTrue(DemoConsumerExperience().interestHistory().isEmpty())
    }
    @Test fun localCategoryEditsReachPublicCatalogueWithoutValuesAndKeepOtherSellersUnchanged() = runTest {
        val demo = seller(); val first = demo.ownVendor().products.first(); val other = demo.catalogue[1]
        demo.saveDemoCategory(first.id, "Salgados assados")
        demo.saveDemoCategory(null, "Lanches naturais")
        assertEquals(3, demo.ownVendor().products.size)
        assertEquals(other, demo.catalogue[1]); demo.setDemoOnline(true)
        val row = assertIs<UiState.Success<List<Vendor>>>(demo.vendors(vendorId = demo.ownedVendorId).first()).data.single()
        assertEquals("Salgados assados", row.products.first().name)
        assertTrue(row.products.all { it.price.isEmpty() })
        val added = row.products.last(); assertTrue(added.id.startsWith("demo-")); assertEquals("demo-product-naturais", demo.artworkId(added.id))
    }
    @Test fun sellerCannotEditForeignCategoryAddDuplicatesOrOfferMadeToOrderItems() {
        val demo = seller(); val before = demo.ownVendor()
        assertFailsWith<IllegalArgumentException> { demo.saveDemoCategory(demo.catalogue[1].products.first().id, "Pães") }
        assertFailsWith<IllegalArgumentException> { demo.saveDemoCategory(null, "Hambúrguer feito na hora") }
        assertFailsWith<IllegalArgumentException> { demo.saveDemoCategory(null, "Pães") }
        assertEquals(before, demo.ownVendor())
    }
    @Test fun incomingInterestAllowsSimulatedContactWithoutOrderOrStockGuarantee() {
        val demo = seller(); assertFailsWith<IllegalStateException> { demo.simulateCustomer() }
        demo.setDemoOnline(true); demo.publishDemoLocation()
        val interest = demo.simulateCustomer(); assertEquals(DemoIntentStatus.Pending, demo.incomingIntents().single().status)
        demo.saveDemoCategory(interest.productId, "Salgados assados")
        assertEquals("Pães", interest.productName) // Immutable category snapshot, no price/quantity fields.
        demo.advanceDemoIntent(interest.protocol); assertEquals(DemoIntentStatus.Accepted, demo.incomingIntents().single().status)
        demo.advanceDemoIntent(interest.protocol); assertEquals(DemoIntentStatus.Completed, demo.incomingIntents().single().status)
        assertFailsWith<IllegalStateException> { demo.advanceDemoIntent(interest.protocol) }
    }
    @Test fun incomingIntentsAreScopedToTheOwnerAndSellerMutationsResetOnExit() {
        val demo = seller(); val other = demo.catalogue[1]; val foreign = demo.consultAvailability(other.id, other.products.first().id)
        assertTrue(demo.incomingIntents().isEmpty())
        assertFailsWith<IllegalStateException> { demo.advanceDemoIntent(foreign.protocol) }
        demo.setDemoOnline(true); demo.publishDemoLocation(); demo.simulateCustomer()
        assertTrue(seller().incomingIntents().isEmpty()); assertEquals("Pães", seller().ownVendor().products.first().name)
        assertFailsWith<IllegalStateException> { DemoConsumerExperience().saveDemoCategory(null, "Pães") }
    }
}
