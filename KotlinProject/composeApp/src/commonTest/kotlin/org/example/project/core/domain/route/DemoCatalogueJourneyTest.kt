package org.example.project.core.domain.route

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.example.project.core.data.Vendor
import org.example.project.core.utils.Location
import org.example.project.core.utils.UiState
import org.example.project.features.demo.*

class DemoCatalogueJourneyTest {
    private fun seller() = DemoConsumerExperience().apply { register("Jose Demo", DemoRole.Vendor, "Pães") }
    private suspend fun automatic() : DemoAutomaticAttendance {
        val routes = DemoRouteSession()
        val id = routes.savePreparedRoute("Rota fictícia", DemoAutomaticAttendance.fixtureWaypoints)
        return DemoAutomaticAttendance(routes).also { it.select(id); it.start() }
    }
    @Test fun newlyAddedCategoryFlowsFromOwnerCatalogueIntoTheSameAutomaticQueue() = runTest {
        val catalogue = seller(); val other = catalogue.catalogue[1]
        catalogue.saveDemoCategory(null, "Lanches naturais")
        val journey = automatic(); val engine = journey.attendance.value!!
        catalogue.applyJourneySnapshot(engine.state.value)
        val visible = assertIs<UiState.Success<List<Vendor>>>(catalogue.vendors(vendorId = catalogue.ownedVendorId).first()).data.single()
        val added = visible.products.single { it.name == "Lanches naturais" }
        assertEquals("", added.price)
        journey.describeConsumerContext(visible, added)
        assertEquals(visible.name, journey.consumerContext.value.vendorName)
        assertEquals(added.name, journey.consumerContext.value.category)
        val protocol = journey.consumerInterest(journey.meetingPoint(0))
        assertEquals(protocol, engine.state.value.queue.single().intent.id)
        assertEquals(protocol, journey.consumerInterest(journey.meetingPoint(0)))
        assertEquals(other, catalogue.catalogue[1])
        assertTrue(catalogue.interestHistory().isEmpty()) // No second legacy receipt was fabricated.
    }
    @Test fun journeyControlsVisibilityAndSyntheticPositionOnlyForTheRegisteredOwner() = runTest {
        val catalogue = seller(); val journey = automatic(); val e = journey.attendance.value!!
        catalogue.applyJourneySnapshot(e.state.value)
        assertTrue(catalogue.ownVendor().isOnline)
        assertEquals(e.state.value.location!!.latitude, catalogue.ownVendor().latitude)
        journey.pause(); catalogue.applyJourneySnapshot(e.state.value)
        assertFalse(catalogue.ownVendor().isOnline); assertNull(catalogue.ownVendor().latitude)
        journey.resume(); catalogue.applyJourneySnapshot(e.state.value)
        assertTrue(catalogue.ownVendor().isOnline)
        journey.end(); catalogue.applyJourneySnapshot(e.state.value)
        assertFalse(catalogue.ownVendor().isOnline); assertNull(catalogue.ownVendor().longitude)
    }
    @Test fun routeEligibilityDoesNotCaptureOtherVendorsConsumersOrEndedJourneys() = runTest {
        val catalogue = seller(); val journey = automatic(); val e = journey.attendance.value!!
        assertTrue(catalogue.canUseAutomaticJourney(catalogue.ownedVendorId, e.state.value))
        assertFalse(catalogue.canUseAutomaticJourney(catalogue.catalogue[1].id, e.state.value))
        assertFalse(DemoConsumerExperience().canUseAutomaticJourney(catalogue.ownedVendorId, e.state.value))
        assertFalse(catalogue.canUseAutomaticJourney(catalogue.ownedVendorId, null))
        journey.pause(); assertTrue(catalogue.canUseAutomaticJourney(catalogue.ownedVendorId, e.state.value))
        journey.end(); assertFalse(catalogue.canUseAutomaticJourney(catalogue.ownedVendorId, e.state.value))
    }
    @Test fun invalidOrUnreliablePositionsAreNotExposedInTheLocalPublicCatalogue() {
        val catalogue = seller()
        for (snapshot in listOf(
            AttendanceSnapshot(JourneyPhase.Working, location = Location(Double.NaN, 0.0), gpsIssue = null),
            AttendanceSnapshot(JourneyPhase.Working, location = Location(-23.55, -46.63), gpsIssue = GpsIssue.Stale),
            AttendanceSnapshot(JourneyPhase.Working, connected = false, location = Location(-23.55, -46.63), gpsIssue = null)
        )) { catalogue.applyJourneySnapshot(snapshot); assertFalse(catalogue.ownVendor().isOnline); assertNull(catalogue.ownVendor().latitude) }
        assertFailsWith<IllegalStateException> { DemoConsumerExperience().applyJourneySnapshot(AttendanceSnapshot()) }
    }
    @Test fun consumerContextRejectsForeignProductsAndKeepsDefaultsAvailable() = runTest {
        val catalogue = seller(); val journey = automatic(); val owner = catalogue.ownVendor()
        assertEquals("Pães", journey.consumerContext.value.category)
        assertFailsWith<IllegalArgumentException> { journey.describeConsumerContext(owner, catalogue.catalogue[1].products.first()) }
        journey.describeConsumerContext(owner, owner.products.first())
        assertEquals(owner.name, journey.consumerContext.value.vendorName)
    }
}
