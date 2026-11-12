package org.example.project.core.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

/** The website and app share the same persisted vendor identity and catalog visibility. */
class OwnedCatalogueContractTest {
    private val userId = "10000000-0000-4000-8000-000000000001"
    private val vendorId = "20000000-0000-4000-8000-000000000002"

    @Test fun canonicalConfirmedVendorIdentityIsUsedForProductOwnership() = runTest {
        val profile = AccountProfileRepository { AccountProfile(userId, "vendor", vendorId) }
        assertEquals(vendorId, profile.vendorIdFor(userId))
        assertEquals("vendor", profile.roleFor(userId))
    }

    @Test fun anotherAccountCannotInheritVendorIdentity() = runTest {
        val profile = AccountProfileRepository { AccountProfile(userId, "vendor", vendorId) }
        assertFailsWith<IllegalStateException> {
            profile.vendorIdFor("10000000-0000-4000-8000-000000000099")
        }
    }

    @Test fun consumerAndIncompleteVendorCannotManageCatalog() = runTest {
        assertFailsWith<IllegalStateException> {
            AccountProfileRepository { AccountProfile(userId, "consumer") }.vendorIdFor(userId)
        }
        assertFailsWith<ProfessionalProfileIncomplete> {
            AccountProfileRepository { AccountProfile(userId, "vendor_incomplete") }.vendorIdFor(userId)
        }
    }

    @Test fun websiteCreatedProductsKeepVisibilityWithoutLosingPricePrecision() {
        val active = Json.decodeFromString<ProductRecord>(
            """{"id":"a","name":"Pão integral","price":0,"is_active":true}"""
        )
        val hidden = Json.decodeFromString<ProductRecord>(
            """{"id":"b","name":"Bolo","price":"12.50","is_active":false}"""
        )
        assertEquals(OwnCatalogProduct("a", "Pão integral", "0.00", true), active.toOwned())
        assertEquals(OwnCatalogProduct("b", "Bolo", "12.50", false), hidden.toOwned())
        assertEquals("Bolo", hidden.toDomain().name)
    }
}
