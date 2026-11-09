package com.loadmate.app.feature.trip.presentation.driving

import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.core.network.*
import com.loadmate.app.core.notify.DriverNotifier
import com.loadmate.app.feature.trip.domain.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 뒷문이 닫히면 계획 수량을 내린 것으로 서버에 묻는다. 관측과 맞으면 서버가 완료하고 기사에게 묻지 않는다.
 * 수량이 다르거나 비교할 관측이 없을 때만 팝업·음성으로 알린다. 기사가 답하면 같은 요청 ID로 완료한다.
 */
internal fun TripCoordinator.completeUnload(driverConfirmed: Boolean) {
    val s = local.value
    val trip = s.trip ?: return
    val stop = trip.currentStop ?: return
    if (s.busy) return
    val op = "complete:${stop.id}"
    val key = keyFor(op)
    local.update { it.copy(busy = true) }
    scope.launch {
        when (val r = repo.completeStop(trip.id, stop.id, key, stop.plannedCounts, driverConfirmed)) {
            is ApiResult.Success -> {
                val res = r.value
                when {
                    res.completed -> {
                        done(op)
                        local.update {
                            it.copy(busy = false, pendingConfirmation = null, message = UiMessage("${res.stop.label} 하차 완료 · 남은 짐 ${countsText(res.expectedRemaining, trip)}", false))
                        }
                        continueRoute()
                    }
                    res.comparison.state == ComparisonState.MISMATCH -> {
                        local.update { it.copy(busy = false, pendingConfirmation = res.comparison) }
                        speech?.speak(mismatchSpeech(res.comparison, trip, stop.label))
                        notifyIfHidden(stop)
                    }
                    else -> {
                        local.update { it.copy(busy = false, unloadAskStopId = stop.id) }
                        speech?.speak("${stop.label} 하차가 끝났는지 확인해 주세요.")
                        notifyIfHidden(stop)
                    }
                }
            }
            is ApiResult.Failure -> {
                if ((r.error as? ApiError.Http)?.code == "stop_already_completed") {
                    done(op)
                    reloadQuietly()
                } else if (!driverConfirmed) {
                    // 서버에 닿지 못했다. 팝업을 열어 두어 기사가 같은 요청 ID로 다시 보낼 수 있게 한다
                    local.update { it.copy(unloadAskStopId = stop.id) }
                }
                fail(r.error)
            }
        }
    }
}

private fun TripCoordinator.notifyIfHidden(stop: Stop) {
    if (!appVisible.value) notify(DriverNotifier.ID_ARRIVAL, "${stop.label} 하차 확인", "눌러서 확인")
}

/** 완료 후 운행을 다시 받고, 이번 안내에 없던 방문지가 다음 차례면 내비에 바로 이어서 보낸다 */
private fun TripCoordinator.continueRoute() {
    scope.launch {
        val trip = (repo.getTrip(tripId) as? ApiResult.Success)?.value ?: return@launch
        applyTrip(trip)
        val sent = local.value.lastRoutePlan ?: return@launch
        if (!trip.isEnded && needsNextRoute(sent.stopLabels, trip.pendingStops)) askRoute(trip, confirm = false)
    }
}

/** 좌표가 없는 방문지는 서버에서 받아 이번 전달에만 쓴다. confirm 이면 확인창을 열고, 아니면 바로 내비에 보낸다 */
internal fun TripCoordinator.askRoute(trip: Trip? = local.value.trip, confirm: Boolean = true, announceGap: Boolean = true) {
    val stops = routeStops(trip?.pendingStops ?: return)
    if (announceGap && routeLoadGap(trip) != null) speech?.speak(ROUTE_LOAD_GAP_SPEECH)
    val deliver: (List<Stop>) -> Unit = { located ->
        val plan = routePlan(located)
        local.update { it.copy(busy = false, routeDialog = plan.takeIf { confirm }, lastRoutePlan = if (confirm) it.lastRoutePlan else plan ?: it.lastRoutePlan) }
        if (!confirm && plan != null) navi.requestRoute(plan.destination, plan.waypoints)
    }
    val unresolved = stops.filter { it.latitude == null || it.longitude == null }
    if (unresolved.isEmpty()) {
        deliver(stops)
        return
    }
    if (local.value.busy) return
    local.update { it.copy(busy = true) }
    scope.launch {
        when (val r = repo.routePoints(trip.id, unresolved.map { it.id })) {
            is ApiResult.Success -> {
                val notFound = unresolved.filter { it.id in r.value.missing }
                if (notFound.isEmpty()) {
                    deliver(withRoutePoints(stops, r.value.points))
                } else {
                    val labels = notFound.joinToString(", ") { it.label }
                    local.update { it.copy(busy = false, message = UiMessage("$labels 주소를 찾지 못했습니다", true)) }
                }
            }
            is ApiResult.Failure -> fail(r.error)
        }
    }
}

internal fun TripCoordinator.sendRoute() {
    val plan = local.value.routeDialog ?: return
    local.update { it.copy(routeDialog = null, lastRoutePlan = plan) }
    navi.requestRoute(plan.destination, plan.waypoints)
}
