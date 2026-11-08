package com.loadmate.app.feature.trip.presentation

import com.loadmate.app.core.pleos.NaviDestination
import com.loadmate.app.core.pleos.NaviEvent
import com.loadmate.app.core.pleos.NaviGateway
import com.loadmate.app.feature.trip.domain.Stop

/** 내비에 보낼 경로: 남은 방문지 앞에서부터 최대 (경유지 2 + 목적지 1). 4곳 이상이면 다음 전달 때 이어서 보낸다. */
data class RoutePlan(val destination: NaviDestination, val waypoints: List<NaviDestination>, val stopLabels: List<String>)

internal fun routePlan(pending: List<Stop>): RoutePlan? {
    val take = pending.take(NaviGateway.MAX_WAYPOINTS + 1)
    if (take.isEmpty()) return null
    val dests = take.map { NaviDestination("${it.label} · ${it.name}", it.latitude, it.longitude, it.address) }
    return RoutePlan(dests.last(), dests.dropLast(1), take.map { it.label })
}

/** 마지막 목적지 전달 요청의 결과 (기획서 16장: 요청 실패를 길 안내 시작으로 표시하지 않음) */
sealed interface RouteRequestStatus {
    data object None : RouteRequestStatus
    data object Waiting : RouteRequestStatus
    data class Started(val name: String, val distanceMeters: Int, val durationSeconds: Int) : RouteRequestStatus
    data class Failed(val reason: String) : RouteRequestStatus
    data object Cancelled : RouteRequestStatus
}

/**
 * NaviState.events(최신이 앞)에서 마지막 요청 이후 결과를 읽는다.
 * 실측(2026-11-08): 요청 직후 기존 경로의 onRouteCancelled 가 먼저 오고, 성공 시 onRouteStarted 직후 onRouteEnded 가 붙어 온다
 * → 요청 직후의 취소·종료는 무시하고, 안내 시작 이후의 취소만 "취소됨"으로 본다.
 */
internal fun routeRequestStatus(eventsNewestFirst: List<NaviEvent>): RouteRequestStatus {
    val idx = eventsNewestFirst.indexOfFirst { it is NaviEvent.RouteRequested }
    if (idx < 0) {
        // 요청이 앱 쪽에서 막힌 경우(미연결 등) RouteRequested 없이 RequestFailed 만 남는다
        val failed = eventsNewestFirst.firstOrNull { it is NaviEvent.RequestFailed } as NaviEvent.RequestFailed?
        return failed?.let { RouteRequestStatus.Failed(it.reason) } ?: RouteRequestStatus.None
    }
    var status: RouteRequestStatus = RouteRequestStatus.Waiting
    for (e in eventsNewestFirst.subList(0, idx).asReversed()) {
        status = when (e) {
            is NaviEvent.RouteStarted -> RouteRequestStatus.Started(e.name, e.distanceMeters, e.durationSeconds)
            is NaviEvent.Error -> RouteRequestStatus.Failed(e.code)
            is NaviEvent.RequestFailed -> RouteRequestStatus.Failed(e.reason)
            is NaviEvent.RouteCancelled -> if (status is RouteRequestStatus.Started) RouteRequestStatus.Cancelled else status
            else -> status
        }
    }
    return status
}

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

/**
 * 샘플 관측의 구역 배치 (F12 시연용, 화면에 "샘플"로 표시).
 * 계획대로 내렸다고 가정한 배치에서 시작해, 입력한 관측 수량과의 차이를 지금 방문지의 구역에 둔다
 * → "A 구역에 1개가 남아 보임" 같은 근거 구역을 재현한다.
 */
internal fun sampleZones(
    trip: com.loadmate.app.feature.trip.domain.Trip,
    observed: Map<String, Int>,
    currentStopId: String?,
    delivered: Map<String, Int>,
): Map<String, Map<String, Int>> {
    val zones = plannedZones(trip).mapValues { it.value.toMutableMap() }.toMutableMap()
    val current = trip.stops.firstOrNull { it.id == currentStopId }
    val hereZone = current?.cargo?.firstOrNull()?.zone ?: zones.keys.firstOrNull() ?: "rear"
    delivered.forEach { (k, v) -> zones[hereZone]?.let { z -> z[k] = ((z[k] ?: 0) - v).coerceAtLeast(0) } }
    val base = mutableMapOf<String, Int>()
    zones.values.forEach { z -> z.forEach { (k, v) -> base.merge(k, v, Int::plus) } }
    observed.forEach { (k, v) ->
        val diff = v - (base[k] ?: 0)
        if (diff != 0) {
            val z = zones.getOrPut(hereZone) { mutableMapOf() }
            z[k] = ((z[k] ?: 0) + diff).coerceAtLeast(0)
        }
    }
    return zones.mapValues { (_, z) -> z.filterValues { it > 0 } }.filterValues { it.isNotEmpty() }
}

/** 시연용 샘플 추가 방문지 (실제 납품처 아님, 역 근처 좌표) */
internal data class SamplePlace(val name: String, val address: String, val lat: Double, val lng: Double)

internal val SAMPLE_PLACES = listOf(
    SamplePlace("미금역 납품처 (샘플)", "경기 성남시 분당구 미금역 인근", 37.3500, 127.1089),
    SamplePlace("오리역 납품처 (샘플)", "경기 성남시 분당구 오리역 인근", 37.3399, 127.1089),
)
