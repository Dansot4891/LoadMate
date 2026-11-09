package com.loadmate.app.feature.trip.presentation.cargo

import com.loadmate.app.feature.trip.domain.ObservationSource
import com.loadmate.app.feature.trip.domain.PlacementEvent
import com.loadmate.app.feature.trip.domain.PlacementType
import com.loadmate.app.feature.trip.presentation.shared.TripCoordinator
import kotlinx.coroutines.flow.update
import java.time.temporal.ChronoUnit

private fun TripCoordinator.iso() = now().truncatedTo(ChronoUnit.SECONDS).toString()

internal fun TripCoordinator.acceptUnitPosition(unitId: Int) {
    change("accept:$unitId", "이 자리로 바꿨습니다") { t, k ->
        repo.recordPlacements(t.id, k, ObservationSource.MANUAL, iso(), listOf(PlacementEvent(unitId, PlacementType.ACCEPTED)))
    }
}

/** 기사가 계획 자리로 다시 옮겼다고 확인한 경우 */
internal fun TripCoordinator.restoreUnitPosition(unitId: Int) {
    val planned = local.value.trip?.units?.firstOrNull { it.id == unitId }?.plannedSlot ?: return
    change("restore:$unitId", "계획 자리로 옮겼습니다") { t, k ->
        repo.recordPlacements(t.id, k, ObservationSource.MANUAL, iso(), listOf(PlacementEvent(unitId, PlacementType.MOVED, planned)))
    }
}

internal fun TripCoordinator.submitPickup() {
    val d = local.value.pickupDraft ?: return
    val trip = local.value.trip ?: return
    val stopId = trip.currentStop?.id
    change("pickup:$stopId:${d.itemName}:${d.qty}:${d.returnPlace}", "회수 기록 · ${d.itemName} ${d.qty}개 → ${d.returnPlace}") { t, k ->
        repo.addPickup(t.id, k, stopId, d.itemName, d.kind, d.qty, d.returnPlace, "screen")
    }
    local.update { it.copy(pickupDraft = null) }
}
