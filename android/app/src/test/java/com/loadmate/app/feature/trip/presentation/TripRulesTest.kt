package com.loadmate.app.feature.trip.presentation

import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.core.pleos.RouteRequestStatus
import com.loadmate.app.core.pleos.routeRequestStatus

import com.loadmate.app.core.pleos.NaviEvent
import com.loadmate.app.feature.trip.domain.Stop
import com.loadmate.app.feature.trip.domain.StopStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripRulesTest {
    private fun stop(label: String) = Stop("s-$label", 0, label, "$label 납품처", "", 37.0, 127.0, StopStatus.PENDING, null, emptyList())

    @Test
    fun `남은 방문지 3곳은 경유지 2 + 목적지 1`() {
        val p = routePlan(listOf(stop("A"), stop("B"), stop("C")))!!
        assertEquals(listOf("A · A 납품처", "B · B 납품처"), p.waypoints.map { it.name })
        assertEquals("C · C 납품처", p.destination.name)
        assertEquals(listOf("A", "B", "C"), p.stopLabels)
    }

    @Test
    fun `4곳 이상이면 앞의 3곳만, 1곳이면 경유지 없음, 없으면 null`() {
        assertEquals(listOf("A", "B", "C"), routePlan(listOf(stop("A"), stop("B"), stop("C"), stop("D")))!!.stopLabels)
        assertEquals(0, routePlan(listOf(stop("C")))!!.waypoints.size)
        assertNull(routePlan(emptyList()))
    }

    // events 는 최신이 앞
    @Test
    fun `실측 성공 순서 - 기존 경로 취소, 시작, 종료가 와도 안내 시작`() {
        val ev = listOf(
            NaviEvent.StatusChanged("ROUTE_DRIVE", 6),
            NaviEvent.RouteEnded(5),
            NaviEvent.RouteStarted("A", 3100, 660, 4),
            NaviEvent.StatusChanged("FREE_DRIVE", 3),
            NaviEvent.RouteCancelled(2),
            NaviEvent.RouteRequested("A → B → C", 1),
        )
        assertEquals(RouteRequestStatus.Started("A", 3100, 660), routeRequestStatus(ev))
    }

    @Test
    fun `실측 실패 - CAUSE_SEARCH_ROUTE 는 실패로 표시, 시작으로 표시하지 않음`() {
        val ev = listOf(NaviEvent.Error("CAUSE_SEARCH_ROUTE", 4), NaviEvent.RouteCancelled(3), NaviEvent.RouteRequested("X", 1))
        assertEquals(RouteRequestStatus.Failed("CAUSE_SEARCH_ROUTE"), routeRequestStatus(ev))
    }

    @Test
    fun `응답 전에는 대기, 시작 후 취소는 취소됨, 이전 요청 결과는 무시`() {
        assertEquals(RouteRequestStatus.Waiting, routeRequestStatus(listOf(NaviEvent.RouteRequested("A", 5), NaviEvent.RouteStarted("old", 1, 1, 4))))
        assertEquals(
            RouteRequestStatus.Cancelled,
            routeRequestStatus(listOf(NaviEvent.RouteCancelled(3), NaviEvent.RouteStarted("A", 1, 1, 2), NaviEvent.RouteRequested("A", 1))),
        )
        assertEquals(RouteRequestStatus.None, routeRequestStatus(emptyList()))
        assertEquals(RouteRequestStatus.Failed("내비 서비스 미연결"), routeRequestStatus(listOf(NaviEvent.RequestFailed("내비 서비스 미연결", 1))))
    }

    @Test
    fun `샘플 구역 배치 - A 에서 1개 덜 내리면 차이가 A 구역(뒷문 쪽)에 남는다`() {
        val stops = listOf(
            Stop("a", 1, "A", "A", "", 0.0, 0.0, StopStatus.PENDING, null, listOf(com.loadmate.app.feature.trip.domain.CargoLine(1, "박스", "box", 3, 0, "rear"))),
            Stop("b", 2, "B", "B", "", 0.0, 0.0, StopStatus.PENDING, null, listOf(com.loadmate.app.feature.trip.domain.CargoLine(2, "박스", "box", 3, 0, "middle"))),
            Stop("c", 3, "C", "C", "", 0.0, 0.0, StopStatus.PENDING, null, listOf(com.loadmate.app.feature.trip.domain.CargoLine(3, "박스", "box", 4, 0, "front"))),
        )
        val cmp = com.loadmate.app.feature.trip.domain.Comparison(com.loadmate.app.feature.trip.domain.ComparisonState.MATCH, null, null, emptyList(), null, true, 300)
        val trip = com.loadmate.app.feature.trip.domain.Trip("t", "t", "sample", com.loadmate.app.feature.trip.domain.TripStatus.IN_PROGRESS, null, stops, mapOf("box" to 10), cmp)
        assertEquals(mapOf("rear" to mapOf("box" to 3), "middle" to mapOf("box" to 3), "front" to mapOf("box" to 4)), plannedZones(trip))
        val z = sampleZones(trip, mapOf("box" to 8), "a", mapOf("box" to 3))
        assertEquals(mapOf("rear" to mapOf("box" to 1), "middle" to mapOf("box" to 3), "front" to mapOf("box" to 4)), z)
    }
}
