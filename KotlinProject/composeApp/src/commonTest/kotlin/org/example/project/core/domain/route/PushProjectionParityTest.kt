package org.example.project.core.domain.route
import kotlin.test.*
import org.example.project.core.domain.models.RouteWaypoint
import org.example.project.core.utils.Location
class PushProjectionParityTest {
 @Test fun case0() {
  val p = RouteProgress(listOf(RouteWaypoint(0.0, 0.0, 0),RouteWaypoint(0.0, 0.01, 1))).project(Location(0.0, 0.005))!!
  assertEquals(false, p.ambiguous, "straight")
  assertEquals(555.975, p.alongMeters, 0.001, "straight")
  assertEquals(0.0, p.crossTrackMeters, 0.001, "straight")
 }
 @Test fun case1() {
  val p = RouteProgress(listOf(RouteWaypoint(0.0, 0.0, 0),RouteWaypoint(0.0, 0.01, 1))).project(Location(0.0, -0.001))!!
  assertEquals(false, p.ambiguous, "clamped behind")
  assertEquals(0.0, p.alongMeters, 0.001, "clamped behind")
  assertEquals(111.195, p.crossTrackMeters, 0.001, "clamped behind")
 }
 @Test fun case2() {
  val p = RouteProgress(listOf(RouteWaypoint(0.0, 0.0, 0),RouteWaypoint(0.0, 0.01, 1))).project(Location(0.002, 0.005))!!
  assertEquals(false, p.ambiguous, "outside corridor")
  assertEquals(555.975, p.alongMeters, 0.001, "outside corridor")
  assertEquals(222.39, p.crossTrackMeters, 0.001, "outside corridor")
 }
 @Test fun case3() {
  val p = RouteProgress(listOf(RouteWaypoint(0.0, 0.0, 0),RouteWaypoint(0.0, 0.01, 1),RouteWaypoint(0.0, 0.0, 2))).project(Location(0.0, 0.005))!!
  assertEquals(true, p.ambiguous, "overlap return")
 }
 @Test fun case4() {
  val p = RouteProgress(listOf(RouteWaypoint(0.0, 0.0, 0),RouteWaypoint(0.0, 0.01, 1),RouteWaypoint(0.01, 0.01, 2),RouteWaypoint(0.01, 0.0, 3),RouteWaypoint(0.0, 0.0, 4))).project(Location(0.0, 0.0))!!
  assertEquals(true, p.ambiguous, "closed loop origin")
 }
 @Test fun case5() {
  val p = RouteProgress(listOf(RouteWaypoint(-0.005, -0.005, 0),RouteWaypoint(0.005, 0.005, 1),RouteWaypoint(-0.005, 0.005, 2),RouteWaypoint(0.005, -0.005, 3))).project(Location(0.0, 0.0))!!
  assertEquals(true, p.ambiguous, "crossing")
 }
 @Test fun case6() {
  val p = RouteProgress(listOf(RouteWaypoint(0.0, 0.0, 0),RouteWaypoint(0.0, 0.0, 1),RouteWaypoint(0.0, 0.01, 2))).project(Location(0.0, 0.005))!!
  assertEquals(false, p.ambiguous, "duplicate vertex")
  assertEquals(555.975, p.alongMeters, 0.001, "duplicate vertex")
  assertEquals(0.0, p.crossTrackMeters, 0.001, "duplicate vertex")
 }
}
