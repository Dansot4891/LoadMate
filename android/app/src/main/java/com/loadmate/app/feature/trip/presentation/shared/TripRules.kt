package com.loadmate.app.feature.trip.presentation.shared

import com.loadmate.app.core.pleos.NaviDestination
import com.loadmate.app.core.pleos.NaviEvent
import com.loadmate.app.core.pleos.NaviGateway
import com.loadmate.app.feature.trip.domain.RoutePoint
import com.loadmate.app.feature.trip.domain.CargoUnitState
import com.loadmate.app.feature.trip.domain.Stop
import com.loadmate.app.feature.trip.domain.StopStatus

/** 내비에 보낼 경로: 남은 방문지 앞에서부터 최대 (경유지 2 + 목적지 1). 4곳 이상이면 다음 전달 때 이어서 보낸다. */
data class RoutePlan(val destination: NaviDestination, val waypoints: List<NaviDestination>, val stopLabels: List<String>)

internal fun routeStops(pending: List<Stop>): List<Stop> = pending.take(NaviGateway.MAX_WAYPOINTS + 1)

/** 지금 차례의 방문지 중 이미 보낸 안내에 없는 곳이 있으면 다시 보내야 한다 */
internal fun needsNextRoute(sentLabels: List<String>, pending: List<Stop>): Boolean = routeStops(pending).any { it.label !in sentLabels }

/** 남은 방문지에 배정된 짐과 실제 실린 짐이 다를 때의 두 수량. 표시용이며 잔량 판정은 서버 값이다 */
internal data class RouteLoadGap(val route: Map<String, Int>, val loaded: Map<String, Int>)

internal fun routeLoadGap(trip: com.loadmate.app.feature.trip.domain.Trip): RouteLoadGap? {
    val expected = trip.expectedRemaining ?: departureLoad(trip).counts
    fun totals(lines: List<Pair<String, Int>>) = lines.groupingBy { it.first }.fold(0) { sum, line -> sum + line.second }
    // 회수품과 경로에서 뺀 방문지의 짐은 차에 있어도 내릴 곳이 없으므로 비교에서 제외한다
    val notRoute = totals(
        trip.loadedPickups.map { it.kind to it.qty } +
            trip.stops.filter { it.status == StopStatus.CANCELLED }.flatMap { s -> s.cargo.map { it.kind to it.plannedQty } },
    )
    val loaded = expected.mapValues { (kind, n) -> n - (notRoute[kind] ?: 0) }.filterValues { it > 0 }
    val route = totals(trip.pendingStops.flatMap { s -> s.cargo.map { it.kind to it.plannedQty } }).filterValues { it > 0 }
    return if (route == loaded) null else RouteLoadGap(route, loaded)
}

internal fun routeLoadGapText(trip: com.loadmate.app.feature.trip.domain.Trip): String? =
    routeLoadGap(trip)?.let { "경로 ${countsText(it.route, trip)} · 실린 짐 ${countsText(it.loaded, trip).ifBlank { "없음" }}" }

/** 내비가 한 번에 받지 못해 이번 안내에서 빠지는 방문지 안내. 모두 들어가면 null */
internal fun routeWaitingText(pending: List<Stop>): String? {
    val waiting = pending.drop(routeStops(pending).size)
    if (waiting.isEmpty()) return null
    return "내비는 한 번에 ${routeStops(pending).size}곳까지 받습니다 · ${waiting.joinToString(", ") { it.name }}는 앞 방문지를 마치면 자동 안내"
}

/** 좌표가 빠진 방문지가 있으면 null. 임의 좌표로 보내지 않는다 */
internal fun routePlan(pending: List<Stop>): RoutePlan? {
    val take = routeStops(pending)
    if (take.isEmpty()) return null
    val dests = take.map { NaviDestination("${it.label} · ${it.name}", it.latitude ?: return null, it.longitude ?: return null, it.address) }
    return RoutePlan(dests.last(), dests.dropLast(1), take.map { it.label })
}

/** 목록 안의 세로 위치에 있는 방문지. 높이를 아직 모르거나 목록 밖이면 null */
internal fun stopAtY(order: List<String>, heights: Map<String, Int>, y: Float): String? {
    var top = 0f
    for (id in order) {
        val height = heights[id] ?: return null
        if (y >= top && y < top + height) return id
        top += height
    }
    return null
}

/** 끄는 줄이 이웃 줄 높이의 절반을 넘으면 자리를 바꾼다. 새 순서와 남은 손가락 이동량을 돌려준다 */
internal fun dragStep(order: List<String>, id: String, offset: Float, heights: Map<String, Int>): Pair<List<String>, Float> {
    val out = order.toMutableList()
    var rest = offset
    while (true) {
        val i = out.indexOf(id)
        if (i < 0) break
        val j = if (rest > 0) i + 1 else i - 1
        val height = out.getOrNull(j)?.let { heights[it] } ?: break
        if (kotlin.math.abs(rest) <= height / 2f) break
        out[i] = out[j]
        out[j] = id
        rest -= if (rest > 0) height else -height
    }
    return out to rest
}

/** 서버에서 받은 좌표를 이번 전달에만 입힌다 */
internal fun withRoutePoints(stops: List<Stop>, points: Map<String, RoutePoint>): List<Stop> =
    stops.map { s -> points[s.id]?.let { s.copy(latitude = it.latitude, longitude = it.longitude) } ?: s }

/** 출발 확인에 보여 줄 수량. sourceLabel 이 null 이면 관측이 없어 적재 화면의 박스나 계획 수량을 쓴 것이다 */
internal data class DepartureLoad(val counts: Map<String, Int>, val observationId: Int?, val sourceLabel: String?)

internal fun departureLoad(trip: com.loadmate.app.feature.trip.domain.Trip): DepartureLoad {
    val observation = trip.comparison.observation?.takeIf { it.available }
    observation?.counts?.let { return DepartureLoad(it, observation.id, observation.source.label) }
    // 관측이 없으면 적재 화면의 박스가 실린 짐이다. 방문지·수량을 바꿔도 이 값은 늘지 않는다
    val boxes = trip.units.filter { it.pickupId == null && it.state != CargoUnitState.UNLOADED }.groupingBy { it.kind }.eachCount()
    return DepartureLoad(boxes.ifEmpty { trip.plannedTotals }, null, null)
}

/** 화면 상단 잔량 한 줄. 기준 확정 뒤에는 서버 계산값만 잔량이라 부른다 (원칙 2) */
internal fun remainingHeadline(trip: com.loadmate.app.feature.trip.domain.Trip): String =
    trip.expectedRemaining?.let { "남은 짐 ${countsText(it, trip)}" }
        ?: "계획 ${countsText(trip.plannedTotals, trip)}"

/** 아직 내리지 않은 배송품의 구역별 수량 (F13). 구역 미지정 물품은 "unknown" */
internal fun plannedZones(trip: com.loadmate.app.feature.trip.domain.Trip): Map<String, Map<String, Int>> {
    val out = linkedMapOf<String, MutableMap<String, Int>>()
    trip.stops.filter { it.status == com.loadmate.app.feature.trip.domain.StopStatus.PENDING }.forEach { st ->
        st.cargo.forEach { l ->
            val left = l.plannedQty - l.deliveredQty
            if (left > 0) out.getOrPut(l.zone ?: "unknown") { mutableMapOf() }.merge(l.kind, left, Int::plus)
        }
    }
    trip.loadedPickups.forEach { p -> out.getOrPut("rear") { mutableMapOf() }.merge(p.kind, p.qty, Int::plus) }
    return out
}

/** 서버가 수량이 다르다고 답했을 때 팝업에 보여 줄 한 줄. 관측일 뿐이므로 단정하지 않는다 (원칙 2) */
internal fun unloadDifferenceText(d: com.loadmate.app.feature.trip.domain.Difference, name: String): String {
    val gap = if (d.observed > d.expected) "${d.observed - d.expected}개가 더 보입니다" else "${d.expected - d.observed}개가 덜 보입니다"
    return "$name $gap (예상 ${d.expected}개 · 확인 ${d.observed}개)"
}
