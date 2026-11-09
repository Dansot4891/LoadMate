package com.loadmate.app.feature.trip.presentation.shared

import com.loadmate.app.core.pleos.NaviDestination
import com.loadmate.app.core.pleos.NaviEvent
import com.loadmate.app.core.pleos.NaviGateway
import com.loadmate.app.feature.trip.domain.Stop

/** 내비에 보낼 경로: 남은 방문지 앞에서부터 최대 (경유지 2 + 목적지 1). 4곳 이상이면 다음 전달 때 이어서 보낸다. */
data class RoutePlan(val destination: NaviDestination, val waypoints: List<NaviDestination>, val stopLabels: List<String>)

internal fun routePlan(pending: List<Stop>): RoutePlan? {
    val take = pending.take(NaviGateway.MAX_WAYPOINTS + 1)
    if (take.isEmpty()) return null
    val dests = take.map { NaviDestination("${it.label} · ${it.name}", it.latitude ?: return null, it.longitude ?: return null, it.address) }
    return RoutePlan(dests.last(), dests.dropLast(1), take.map { it.label })
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
