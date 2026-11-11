package org.example.project.core.domain.notifications

import org.example.project.core.domain.route.AttendanceSnapshot
import org.example.project.core.domain.route.JourneyPhase

/** Newness is measured across confirmed full snapshots, never Realtime event count.
 * A first/reconnected/resumed snapshot is a silent baseline, including ineligible records. */
class NewInterestDetector {
    private val seen = mutableSetOf<String>()
    private var baseline = true
    fun suspendAlerts() { baseline = true }
    fun observe(snapshot: AttendanceSnapshot, foreground: Boolean): Set<String> = observe(
        snapshot.entries.map { it.intent.id }.toSet(), snapshot.queue.map { it.intent.id }.toSet(),
        foreground && snapshot.connected && snapshot.phase == JourneyPhase.Working && snapshot.gpsIssue == null
    )
    fun observe(allIds: Set<String>, eligibleIds: Set<String>, live: Boolean): Set<String> {
        require(eligibleIds.all { it in allIds })
        val fresh = if (!baseline && live) eligibleIds - seen else emptySet()
        seen.addAll(allIds)
        baseline = !live
        return fresh
    }
}

data class InterestNotice(val ownerId: String, val routeId: String, val protocolId: String)
private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
fun InterestNotice.valid() = listOf(ownerId, routeId, protocolId).all { uuid.matches(it) }
fun authorizedInterestRoute(notice: InterestNotice, ownerId: String?, vendorId: String?, routeVendorId: String?): String? =
    if (notice.valid() && notice.ownerId == ownerId && vendorId != null && vendorId == routeVendorId) notice.routeId else null
