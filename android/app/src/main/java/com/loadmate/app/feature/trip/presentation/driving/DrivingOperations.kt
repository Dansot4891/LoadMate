package com.loadmate.app.feature.trip.presentation.driving

import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.core.network.*
import com.loadmate.app.core.notify.DriverNotifier
import com.loadmate.app.feature.trip.domain.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

internal fun TripCoordinator.enterSite() {
    val trip = local.value.trip ?: return
    val stop = trip.currentStop ?: return
    val expectedAfter = trip.expectedRemaining.orEmpty().mapValues { (k, v) -> v - (stop.plannedCounts[k] ?: 0) }
    local.update {
        it.copy(
            step = TripStep.SITE_WORK,
            deliveredDraft = stop.plannedCounts,
            // 샘플 관측 기본값 = 계획대로 내렸을 때의 잔량. 시연에서는 여기서 값을 바꿔 미하차를 재현한다
            observationDraft = expectedAfter,
            pendingConfirmation = null,
            stopMemos = emptyList(),
        )
    }
    memos?.let { repo ->
        scope.launch {
            (repo.forStop(trip.id, stop.id) as? ApiResult.Success)?.let { r -> local.update { it.copy(stopMemos = r.value) } }
        }
    }
}

internal fun TripCoordinator.sendRoute() {
    val plan = local.value.routeDialog ?: return
    local.update { it.copy(routeDialog = null, lastRoutePlan = plan) }
    navi.requestRoute(plan.destination, plan.waypoints)
}
