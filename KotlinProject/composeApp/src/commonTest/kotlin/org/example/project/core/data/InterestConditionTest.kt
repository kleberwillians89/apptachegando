package org.example.project.core.data

import kotlin.test.*
class InterestConditionTest {
    private fun receipt(condition: String, status: String = "pending") = PurchaseIntentReceipt("protocol", "vendor", status, "later", condition)
    @Test fun eligibleNeverPromisesSaleOrStock() { assertTrue(receipt("eligible").conditionMessage().contains("Não confirma atendimento ou estoque")) }
    @Test fun waitingGpsOutsideAndBehindHaveDistinctMessages() {
        val texts = listOf("awaiting_availability", "vendor_location_missing", "outside_route", "behind_route", "ambiguous_route").map { receipt(it).conditionMessage() }
        assertEquals(5, texts.distinct().size)
    }
    @Test fun cancelledAndExpiredTakePrecedenceOverEligibility() {
        assertEquals("Interesse cancelado.", receipt("eligible", "cancelled").conditionMessage())
        assertEquals("Interesse expirado.", receipt("eligible", "expired").conditionMessage())
    }
    @Test fun unknownConditionFailsToWaitingRatherThanEligible() { assertTrue(receipt("future-condition").conditionMessage().contains("aguardando")) }
}
