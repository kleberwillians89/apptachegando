package org.example.project.core.domain.notifications

import kotlin.test.*

class InterestAlertsTest {
    private fun NewInterestDetector.read(vararg ids: String, eligible: Set<String> = ids.toSet(), live: Boolean = true) = observe(ids.toSet(), eligible, live)
    @Test fun firstSnapshotAndReopeningNeverAlertHistory() {
        val gate = NewInterestDetector()
        assertTrue(gate.read("old").isEmpty())
        assertEquals(setOf("new"), gate.read("old", "new"))
        assertTrue(NewInterestDetector().read("old", "new").isEmpty())
    }
    @Test fun realtimePollingAndRefreshAlertExactlyOncePerProtocol() {
        val gate = NewInterestDetector(); gate.read()
        assertEquals(setOf("one"), gate.read("one"))
        repeat(10) { assertTrue(gate.read("one").isEmpty()) }
        assertEquals(setOf("two"), gate.read("one", "two"))
    }
    @Test fun reconnectAndPausedResumeSilentlyAbsorbBacklog() {
        val gate = NewInterestDetector(); gate.read()
        gate.suspendAlerts()
        assertTrue(gate.read("during-gap").isEmpty())
        assertEquals(setOf("after"), gate.read("during-gap", "after"))
        assertTrue(gate.read("during-gap", "after", "paused", live = false).isEmpty())
        assertTrue(gate.read("during-gap", "after", "paused", "resume").isEmpty())
    }
    @Test fun oldIneligibleRecordNeverBecomesANewAlertWhenGpsReturns() {
        val gate = NewInterestDetector(); gate.read()
        assertTrue(gate.read("behind", "no-gps", eligible = emptySet()).isEmpty())
        assertTrue(gate.read("behind", "no-gps").isEmpty())
        assertEquals(setOf("fresh"), gate.read("behind", "no-gps", "fresh"))
    }
    @Test fun disappearedProtocolAndCancelledRecordNeverReplay() {
        val gate = NewInterestDetector(); gate.read("cancelled", eligible = emptySet())
        gate.read()
        assertTrue(gate.read("cancelled").isEmpty())
    }
    @Test fun notificationPermissionIsNotAQueueEligibilityInput() {
        val gate = NewInterestDetector(); gate.read()
        val queue = setOf("new")
        val visualHighlight = gate.observe(queue, queue, live = true)
        val notificationPermission = false
        assertEquals(queue, visualHighlight)
        assertFalse(notificationPermission)
        assertTrue(gate.observe(queue, queue, live = true).isEmpty())
    }
    @Test fun notificationRouteRequiresCurrentOwnerAndVendorOwnership() {
        val owner = "11111111-1111-4111-8111-111111111111"
        val route = "22222222-2222-4222-8222-222222222222"
        val protocol = "33333333-3333-4333-8333-333333333333"
        val notice = InterestNotice(owner, route, protocol)
        assertEquals(route, authorizedInterestRoute(notice, owner, "vendor-a", "vendor-a"))
        assertNull(authorizedInterestRoute(notice, "other-user", "vendor-a", "vendor-a"))
        assertNull(authorizedInterestRoute(notice, owner, "vendor-a", "vendor-b"))
        assertNull(authorizedInterestRoute(notice, null, "vendor-a", "vendor-a"))
        assertNull(authorizedInterestRoute(notice.copy(routeId = "../other"), owner, "vendor-a", "vendor-a"))
    }
}
