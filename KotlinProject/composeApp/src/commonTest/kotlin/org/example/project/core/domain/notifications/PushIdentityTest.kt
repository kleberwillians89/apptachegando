package org.example.project.core.domain.notifications

import kotlin.test.*

class PushIdentityTest {
    @Test fun logoutEpochPreventsInFlightTokenRegistrationFromRestoringAssociation() {
        assertTrue(canRegisterPush("owner", "owner", 1, 1, null, false))
        assertFalse(canRegisterPush("owner", "owner", 1, 2, null, false))
        assertFalse(canRegisterPush("owner", null, 1, 1, "owner", false))
    }
    @Test fun lateRenewalCannotBindAfterLogoutOrToAnotherAccount() {
        assertFalse(canRegisterPush("owner", "owner", 2, 2, null, true))
        assertFalse(canRegisterPush("owner", "other", 2, 2, "owner", true))
        assertTrue(canRegisterPush("owner", "owner", 2, 2, "owner", true))
    }
    @Test fun oldTokenVersionCannotDeliverAfterRotation() {
        assertTrue(pushBindingMatches("owner", "new", "owner", "owner", "new", true))
        assertFalse(pushBindingMatches("owner", "new", "owner", "owner", "old", true))
    }
    @Test fun logoutAccountSwitchAndUnconfirmedRegistrationAllBlockPush() {
        assertFalse(pushBindingMatches(null, null, null, "owner", "version", true))
        assertFalse(pushBindingMatches("owner", "version", "other", "owner", "version", true))
        assertFalse(pushBindingMatches("owner", "version", "owner", "owner", "version", false))
    }
    @Test fun oldAndFutureMessagesAreNotNewInterestAlerts() {
        assertTrue(freshPushStamp(1000, 31000, 2000))
        assertFalse(freshPushStamp(1000, 31000, 32000))
        assertFalse(freshPushStamp(10000, 31000, 1000))
    }
    @Test fun malformedAndExcessiveTtlAreRejected() {
        assertFalse(freshPushStamp(-1, 1000, 1000))
        assertFalse(freshPushStamp(1000, 500, 1000))
        assertFalse(freshPushStamp(1000, 100000, 1000))
    }
}
