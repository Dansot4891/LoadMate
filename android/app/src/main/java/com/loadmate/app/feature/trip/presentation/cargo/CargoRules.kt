package com.loadmate.app.feature.trip.presentation.cargo

import com.loadmate.app.core.pleos.RearDoorConnection
import com.loadmate.app.core.pleos.RearDoorState
import com.loadmate.app.feature.trip.domain.CargoSlot
import com.loadmate.app.feature.trip.domain.CargoUnit
import com.loadmate.app.feature.trip.domain.CargoUnitState
import com.loadmate.app.feature.trip.domain.Issue
import com.loadmate.app.feature.trip.domain.Stop
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.presentation.shared.issueLabel
import com.loadmate.app.feature.trip.presentation.shared.zoneName

/** 적재 화면의 뒷문 상태. 구독 전이면 표시하지 않는다 */
internal fun rearDoorText(door: RearDoorState): String? = when (door.connection) {
    RearDoorConnection.Stopped -> null
    is RearDoorConnection.Failed -> "뒷문 신호 없음"
    RearDoorConnection.Subscribed -> when (door.isOpen) {
        true -> "뒷문 열림"
        false -> "뒷문 닫힘"
        null -> "뒷문 확인 중"
    }
}

internal val CARGO_ZONES = listOf("front", "middle", "rear")
internal const val CARGO_LANES = 3
internal const val CARGO_LAYERS = 3

internal fun laneName(lane: Int) = when (lane) { 0 -> "왼쪽"; 1 -> "중앙"; 2 -> "오른쪽"; else -> "" }

internal fun slotText(slot: CargoSlot?): String = slot?.let { "${zoneName(it.zone)} ${laneName(it.lane)} ${it.layer}층" } ?: "자리 미정"

/** 화면 표시 이름. 예: A 박스 2번, 회수 빈 용기 1번 */
internal fun unitName(trip: Trip, u: CargoUnit): String {
    val owner = if (u.pickupId != null) "회수" else trip.stops.firstOrNull { it.id == u.stopId }?.label ?: "-"
    return "$owner ${u.itemName} ${u.no}번"
}

/** 3D·2D 에 그릴 단위: 내린 것과 자리 미정은 뺀다. 계획 단위는 계획 자리에 흐리게 그린다 */
internal fun drawnSlot(u: CargoUnit): CargoSlot? = when (u.state) {
    CargoUnitState.LOADED -> u.slot
    CargoUnitState.PLANNED -> u.plannedSlot
    else -> null
}

internal fun unplacedCount(trip: Trip): Int = trip.units.count { (it.state == CargoUnitState.LOADED || it.state == CargoUnitState.PLANNED) && drawnSlot(it) == null }

/** 이번 방문지에서 꺼낼 순서. 서버의 하차 규칙과 같이 위층부터, 나중에 실린 것부터 */
internal fun unloadOrder(trip: Trip, stop: Stop?): List<CargoUnit> =
    trip.units.filter { it.stopId == stop?.id && it.pickupId == null && it.state == CargoUnitState.LOADED }
        .sortedWith(compareByDescending<CargoUnit> { it.slot?.layer ?: 0 }.thenByDescending { it.loadSeq })

/** 화면에 보여 줄 꺼내는 순서. 기준 확정 전에는 계획 자리로 같은 규칙을 적용한다 */
internal fun previewUnloadOrder(trip: Trip, stop: Stop?): List<CargoUnit> =
    trip.units.filter { it.stopId == stop?.id && it.pickupId == null && drawnSlot(it) != null }
        .sortedWith(compareByDescending<CargoUnit> { drawnSlot(it)?.layer ?: 0 }.thenByDescending { it.loadSeq })

/** 배치도 범례: 방문지별(회수품은 null) 아직 안 내린 물품 수. 예: A 판교역 3개 */
internal fun stopLegend(trip: Trip): List<Pair<String?, String>> {
    val onBoard = trip.units.filter { it.state != CargoUnitState.UNLOADED }
    val stops: List<Pair<String?, String>> = trip.stops.mapNotNull { st ->
        onBoard.count { it.stopId == st.id && it.pickupId == null }.takeIf { it > 0 }?.let { st.label to "${st.label} ${st.name} ${it}개" }
    }
    val pickups = onBoard.count { it.pickupId != null }
    return if (pickups > 0) stops + (null to "회수품 ${pickups}개") else stops
}

/** 이상 안내 문구: 어느 자리의 어떤 물품에 무슨 일이 있는지. 물품을 모르면 구역까지만 적는다 */
internal fun issueText(trip: Trip, issue: Issue): String {
    val unit = trip.units.firstOrNull { it.id == issue.unitId }
    val where = unit?.let { "${slotText(drawnSlot(it))} · ${unitName(trip, it)}" } ?: zoneName(issue.zone)
    return "$where · ${issueLabel(issue.kind)}"
}

/** 구역별 물품 수. 배치도에 그려진 물품과 같은 기준으로 앞쪽부터 센다 */
internal fun zoneCounts(trip: Trip): List<Pair<String, Int>> =
    CARGO_ZONES.map { zone -> zone to trip.units.count { drawnSlot(it)?.zone == zone } }
