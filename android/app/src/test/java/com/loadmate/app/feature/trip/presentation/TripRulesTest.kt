package com.loadmate.app.feature.trip.presentation

import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.core.pleos.RouteRequestStatus
import com.loadmate.app.core.pleos.routeRequestStatus

import com.loadmate.app.core.pleos.NaviEvent
import com.loadmate.app.feature.trip.domain.CargoLine
import com.loadmate.app.feature.trip.domain.Comparison
import com.loadmate.app.feature.trip.domain.ComparisonState
import com.loadmate.app.feature.trip.domain.Stop
import com.loadmate.app.feature.trip.domain.StopStatus
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.domain.TripStatus
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

    @Test
    fun `손가락 위치로 잡은 방문지를 찾는다`() {
        val heights = mapOf("a" to 100, "b" to 120)
        assertEquals("a", stopAtY(listOf("a", "b"), heights, 99f))
        assertEquals("b", stopAtY(listOf("a", "b"), heights, 100f))
        assertNull(stopAtY(listOf("a", "b"), heights, 220f))
        assertNull(stopAtY(listOf("a", "c"), heights, 150f))
    }

    @Test
    fun `끄는 줄은 이웃 높이의 절반을 넘어야 자리를 바꾼다`() {
        val heights = mapOf("a" to 100, "b" to 100, "c" to 100)
        assertEquals(listOf("a", "b", "c") to 40f, dragStep(listOf("a", "b", "c"), "a", 40f, heights))
        assertEquals(listOf("b", "a", "c") to -40f, dragStep(listOf("a", "b", "c"), "a", 60f, heights))
        assertEquals(listOf("b", "c", "a") to -40f, dragStep(listOf("a", "b", "c"), "a", 160f, heights))
        assertEquals(listOf("c", "a", "b") to 40f, dragStep(listOf("a", "b", "c"), "c", -160f, heights))
        assertEquals(listOf("a", "b") to 500f, dragStep(listOf("a", "b"), "b", 500f, mapOf("a" to 100, "b" to 100)))
    }

    @Test
    fun `좌표가 빠진 방문지가 있으면 경로를 만들지 않고 받은 좌표를 입히면 만든다`() {
        val noLocation = stop("A").copy(latitude = null, longitude = null)
        assertNull(routePlan(listOf(noLocation, stop("B"))))
        val filled = withRoutePoints(listOf(noLocation, stop("B")), mapOf("s-A" to com.loadmate.app.feature.trip.domain.RoutePoint(37.5, 127.2)))
        assertEquals(37.5, routePlan(filled)!!.waypoints.single().latitude, 0.0)
        assertEquals(37.0, routePlan(filled)!!.destination.latitude, 0.0)
    }

    @Test
    fun `상단 잔량 - 기준 확정 전에는 잔량이라 부르지 않고 계획을 표시`() {
        val cargo = listOf(CargoLine(1, "박스", "box", 10, 0, "rear"))
        val trip = Trip(
            "t", "운행", "sample", TripStatus.PLANNED, null, listOf(stop("A").copy(cargo = cargo)), null,
            Comparison(ComparisonState.NO_OBSERVATION, null, null, emptyList(), null, true, 0),
        )
        assertEquals("계획 박스 10", remainingHeadline(trip))
        assertEquals("남은 짐 박스 7", remainingHeadline(trip.copy(expectedRemaining = mapOf("box" to 7))))
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
    fun `내비 3곳을 넘는 방문지는 앞 방문지를 마치면 이어 보낸다`() {
        fun stop(id: String, name: String) = Stop(id, 1, id, name, "", 0.0, 0.0, StopStatus.PENDING, null, emptyList())
        val three = listOf(stop("a", "판교역"), stop("b", "정자역"), stop("c", "서현역"))
        val d = stop("d", "성공타워")
        assertEquals(null, routeWaitingText(three))
        assertEquals("내비는 한 번에 3곳까지 받습니다 · 성공타워는 앞 방문지를 마치면 자동 안내", routeWaitingText(three + d))
        assertEquals(false, needsNextRoute(listOf("a", "b", "c"), three.drop(1)))
        assertEquals(true, needsNextRoute(listOf("a", "b", "c"), three.drop(1) + d))
    }

    @Test
    fun `출발 전 실린 짐은 적재 화면 박스이며 방문지를 추가해도 늘지 않는다`() {
        val line = com.loadmate.app.feature.trip.domain.CargoLine(1, "박스", "box", 3, 0, "rear")
        val added = com.loadmate.app.feature.trip.domain.CargoLine(2, "박스", "box", 2, 0, null)
        val a = Stop("a", 1, "A", "A", "", 0.0, 0.0, StopStatus.PENDING, null, listOf(line))
        val d = Stop("d", 2, "D", "D", "", 0.0, 0.0, StopStatus.PENDING, null, listOf(added))
        val units = (1..3).map { com.loadmate.app.feature.trip.domain.CargoUnit(it, "a", null, "box", "박스", it, com.loadmate.app.feature.trip.domain.CargoUnitState.PLANNED, it, null, null, null) }
        val cmp = com.loadmate.app.feature.trip.domain.Comparison(com.loadmate.app.feature.trip.domain.ComparisonState.NO_OBSERVATION, null, null, emptyList(), null, true, 300)
        val trip = com.loadmate.app.feature.trip.domain.Trip("t", "t", "sample", com.loadmate.app.feature.trip.domain.TripStatus.PLANNED, null, listOf(a, d), null, cmp, units = units)
        assertEquals(mapOf("box" to 3), departureLoad(trip).counts)
        assertEquals(RouteLoadGap(mapOf("box" to 5), mapOf("box" to 3)), routeLoadGap(trip))
        assertEquals(null, routeLoadGap(trip.copy(stops = listOf(a))))
    }

    @Test
    fun `경로 짐과 실린 짐 비교 - 경로에서 뺀 방문지의 짐은 제외한다`() {
        fun line(id: Int, qty: Int) = com.loadmate.app.feature.trip.domain.CargoLine(id, "박스", "box", qty, 0, "rear")
        fun stop(id: String, status: StopStatus, qty: Int) = Stop(id, 1, id, id, "", 0.0, 0.0, status, null, listOf(line(id.hashCode(), qty)))
        val cmp = com.loadmate.app.feature.trip.domain.Comparison(com.loadmate.app.feature.trip.domain.ComparisonState.NO_OBSERVATION, null, null, emptyList(), null, true, 300)
        fun trip(stops: List<Stop>, remaining: Int) = com.loadmate.app.feature.trip.domain.Trip("t", "t", "sample", com.loadmate.app.feature.trip.domain.TripStatus.IN_PROGRESS, null, stops, mapOf("box" to remaining), cmp)
        assertEquals(null, routeLoadGap(trip(listOf(stop("a", StopStatus.PENDING, 3), stop("b", StopStatus.CANCELLED, 2)), 5)))
        assertEquals(RouteLoadGap(mapOf("box" to 4), mapOf("box" to 3)), routeLoadGap(trip(listOf(stop("a", StopStatus.PENDING, 3), stop("d", StopStatus.PENDING, 1)), 3)))
    }

    @Test
    fun `하차 수량 차이 문구`() {
        assertEquals("박스 1개가 더 보입니다 (예상 0개 · 확인 1개)", unloadDifferenceText(com.loadmate.app.feature.trip.domain.Difference("box", 0, 1), "박스"))
        assertEquals("박스 1개가 덜 보입니다 (예상 3개 · 확인 2개)", unloadDifferenceText(com.loadmate.app.feature.trip.domain.Difference("box", 3, 2), "박스"))
    }

    @Test
    fun `계획 구역 배치 - 방문지별 구역에 수량이 놓인다`() {
        val stops = listOf(
            Stop("a", 1, "A", "A", "", 0.0, 0.0, StopStatus.PENDING, null, listOf(com.loadmate.app.feature.trip.domain.CargoLine(1, "박스", "box", 3, 0, "rear"))),
            Stop("b", 2, "B", "B", "", 0.0, 0.0, StopStatus.PENDING, null, listOf(com.loadmate.app.feature.trip.domain.CargoLine(2, "박스", "box", 3, 0, "middle"))),
            Stop("c", 3, "C", "C", "", 0.0, 0.0, StopStatus.PENDING, null, listOf(com.loadmate.app.feature.trip.domain.CargoLine(3, "박스", "box", 4, 0, "front"))),
        )
        val cmp = com.loadmate.app.feature.trip.domain.Comparison(com.loadmate.app.feature.trip.domain.ComparisonState.MATCH, null, null, emptyList(), null, true, 300)
        val trip = com.loadmate.app.feature.trip.domain.Trip("t", "t", "sample", com.loadmate.app.feature.trip.domain.TripStatus.IN_PROGRESS, null, stops, mapOf("box" to 10), cmp)
        assertEquals(mapOf("rear" to mapOf("box" to 3), "middle" to mapOf("box" to 3), "front" to mapOf("box" to 4)), plannedZones(trip))
    }
}
