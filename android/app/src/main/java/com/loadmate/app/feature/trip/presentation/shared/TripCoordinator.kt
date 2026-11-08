package com.loadmate.app.feature.trip.presentation.shared

import com.loadmate.app.feature.trip.presentation.loadcheck.recordObservation
import com.loadmate.app.feature.trip.presentation.loadcheck.confirmBaseline
import com.loadmate.app.feature.trip.presentation.driving.enterSite
import com.loadmate.app.feature.trip.presentation.sitework.complete
import com.loadmate.app.feature.trip.presentation.overview.moveStop
import com.loadmate.app.feature.trip.presentation.sitework.submitPickup
import com.loadmate.app.feature.trip.presentation.overview.recordSampleIssue
import com.loadmate.app.feature.trip.presentation.driving.sendRoute
import com.loadmate.app.core.pleos.RouteRequestStatus
import com.loadmate.app.core.pleos.routeRequestStatus

import com.loadmate.app.core.notify.DriverNotifier
import com.loadmate.app.core.pleos.NaviConnection
import com.loadmate.app.core.pleos.NaviGateway
import com.loadmate.app.core.pleos.RearDoorGateway
import com.loadmate.app.core.pleos.RearDoorState
import com.loadmate.app.core.pleos.SpeechOutput
import com.loadmate.app.feature.records.domain.Memo
import com.loadmate.app.feature.records.domain.MemoRepository
import com.loadmate.app.core.network.ApiError
import com.loadmate.app.core.network.describe
import com.loadmate.app.core.network.ApiResult
import com.loadmate.app.feature.trip.domain.Comparison
import com.loadmate.app.feature.trip.domain.Counts
import com.loadmate.app.feature.trip.domain.NewCargo
import com.loadmate.app.feature.trip.domain.ObservationSource
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.domain.TripRepository
import com.loadmate.app.feature.trip.domain.TripStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * 대표 시나리오(14장)와 운행 관리. 잔량·완료 판정은 서버 결과만 사용한다 (원칙 2).
 * 내비 리스너는 운행 중 유지하고 운행 완료·화면 종료 시 해제 (원칙 8).
 * 주행 중에는 화면을 바꾸지 않고 짧은 음성·알림만 쓴다 (R09).
 */
class TripCoordinator(
    internal val repo: TripRepository,
    internal val memos: MemoRepository?,
    internal val navi: NaviGateway,
    internal val rearDoor: RearDoorGateway,
    internal val speech: SpeechOutput?,
    internal val notify: (id: Int, title: String, text: String) -> Unit,
    internal val cached: (String) -> Pair<Trip, Instant>?,
    internal val tripId: String,
    internal val scope: kotlinx.coroutines.CoroutineScope,
    internal val now: () -> Instant = Instant::now,
    internal val newKey: () -> String = { UUID.randomUUID().toString() },
) {

    fun release() {
        rearDoor.stop()
        scope.cancel()
    }

    internal val local = MutableStateFlow(TripUiState())

    val state: StateFlow<TripUiState> =
        combine(local, navi.state, rearDoor.state) { s, n, d ->
            s.copy(naviConnected = n.connection == NaviConnection.Connected, routeStatus = routeRequestStatus(n.events), rearDoor = d)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), TripUiState())

    // 같은 요청의 재시도는 같은 키로 보낸다 (중복 반영 방지). 반영 확정 후에만 비운다.
    private val keys = mutableMapOf<String, String>()
    internal fun keyFor(op: String) = keys.getOrPut(op) { newKey() }
    internal fun done(op: String) { keys.remove(op) }

    init {
        refresh()
        scope.launch { repo.currentTrip.collect { trip -> if (trip != null && trip.id == tripId && trip != local.value.trip) applyTrip(trip, keepStep = true) } }
    }

    fun onAction(a: TripUiAction) {
        when (a) {
            TripUiAction.Refresh -> refresh()
            TripUiAction.OpenLoadCheck -> local.update { it.copy(step = TripStep.LOAD_CHECK, observationDraft = it.trip?.plannedTotals.orEmpty()) }
            TripUiAction.BackToOverview -> local.update { it.copy(step = TripStep.OVERVIEW) }
            is TripUiAction.ObservationCount -> local.update { it.copy(observationDraft = it.observationDraft + (a.kind to a.value)) }
            is TripUiAction.ObservationSourceSelect -> local.update { it.copy(observationSource = a.source) }
            TripUiAction.RecordObservation -> recordObservation(unavailable = false)
            TripUiAction.RecordUnavailable -> recordObservation(unavailable = true)
            TripUiAction.ConfirmBaseline -> confirmBaseline()
            TripUiAction.AskRoute -> local.update { s -> s.copy(routeDialog = s.trip?.let { routePlan(it.pendingStops) }) }
            TripUiAction.ConfirmRoute -> sendRoute()
            TripUiAction.DismissRoute -> local.update { it.copy(routeDialog = null) }
            TripUiAction.Arrived -> enterSite()
            TripUiAction.BackToDriving -> local.update { it.copy(step = TripStep.DRIVING) }
            is TripUiAction.DeliveredCount -> local.update { it.copy(deliveredDraft = it.deliveredDraft + (a.kind to a.value)) }
            TripUiAction.RequestComplete -> complete(driverConfirmed = false)
            TripUiAction.RecheckAfterUnload -> local.update { s ->
                s.copy(step = TripStep.SITE_WORK, pendingConfirmation = null, observationDraft = s.pendingConfirmation?.expected ?: s.observationDraft)
            }
            TripUiAction.DriverConfirmComplete -> complete(driverConfirmed = true)
            TripUiAction.DismissMessage -> local.update { it.copy(message = null) }
            TripUiAction.SiteVisible -> rearDoor.start()
            TripUiAction.SiteHidden -> rearDoor.stop()
            is TripUiAction.AddSampleStop -> SAMPLE_PLACES.getOrNull(a.index)?.let { p ->
                change("addStop:${p.name}", "방문지 추가 · ${p.name}") { t, k ->
                    repo.addStop(t.id, k, p.name, p.address, p.lat, p.lng, listOf(NewCargo("박스", "box", 1)))
                }
            }
            is TripUiAction.MoveStop -> moveStop(a.stopId, a.up)
            is TripUiAction.CancelStop -> change("cancel:${a.stopId}", "방문지 취소 · 물품은 차에 남습니다") { t, k -> repo.cancelStop(t.id, a.stopId, k, a.reason) }
            is TripUiAction.ChangeQty -> change("qty:${a.cargoLineId}:${a.qty}", null) { t, k -> repo.changeCargoQty(t.id, a.stopId, k, a.cargoLineId, a.qty) }
            TripUiAction.AskEndTrip -> local.update { it.copy(endDialog = true) }
            TripUiAction.DismissEndTrip -> local.update { it.copy(endDialog = false) }
            is TripUiAction.EndTrip -> {
                local.update { it.copy(endDialog = false) }
                change("end", "운행을 종료했습니다") { t, k -> repo.endTrip(t.id, k, a.reason) }
            }
            is TripUiAction.OpenPickup -> local.update { it.copy(pickupDraft = a.draft) }
            is TripUiAction.EditPickup -> local.update { it.copy(pickupDraft = a.draft) }
            TripUiAction.DismissPickup -> local.update { it.copy(pickupDraft = null) }
            TripUiAction.SubmitPickup -> submitPickup()
            is TripUiAction.ReturnPickup -> change("return:${a.pickupId}", "반납 처리됨") { t, k -> repo.returnPickup(t.id, a.pickupId, k) }
            is TripUiAction.RecordSampleIssue -> recordSampleIssue(a.kind)
            is TripUiAction.ResolveIssue -> change("resolve:${a.issueId}", "이상 처리 기록됨") { t, k -> repo.resolveIssue(t.id, a.issueId, k, a.resolution, null) }
        }
    }

    // ------------------------------------------------------------------ 서버

    internal fun refresh() {
        local.update { it.copy(loading = true, loadError = null) }
        scope.launch {
            when (val r = repo.getTrip(tripId)) {
                is ApiResult.Success -> applyTrip(r.value, keepStep = true)
                is ApiResult.Failure -> {
                    val c = cached(tripId)
                    if (c != null) {
                        // 최근 받은 실제 값을 오프라인 표시와 함께 유지 (R11). 데모 데이터로 바꾸지 않는다
                        local.update { it.copy(loading = false, trip = c.first, offlineSince = c.second, loadError = describe(r.error)) }
                    } else {
                        local.update { it.copy(loading = false, loadError = describe(r.error)) }
                    }
                }
            }
        }
    }

    internal fun applyTrip(trip: Trip, keepStep: Boolean) {
        local.update { s ->
            val step = when {
                trip.isEnded -> TripStep.DONE
                trip.baseline == null -> if (keepStep && s.step == TripStep.LOAD_CHECK) TripStep.LOAD_CHECK else TripStep.OVERVIEW
                keepStep && s.step in setOf(TripStep.SITE_WORK, TripStep.MISMATCH) && trip.currentStop?.id == s.trip?.currentStop?.id -> s.step
                else -> TripStep.DRIVING
            }
            s.copy(loading = false, loadError = null, offlineSince = null, trip = trip, step = step, lastComparison = trip.comparison)
        }
    }















    /** 공통 변경 요청: 같은 op 는 같은 키로 재시도, 성공 시 운행 갱신 */
    internal fun change(op: String, okText: String?, after: (() -> Unit)? = null, call: suspend (Trip, String) -> ApiResult<Trip>) {
        val s = local.value
        val trip = s.trip ?: return
        if (s.busy) return
        val key = keyFor(op)
        local.update { it.copy(busy = true) }
        scope.launch {
            when (val r = call(trip, key)) {
                is ApiResult.Success -> {
                    done(op)
                    local.update { it.copy(busy = false, message = okText?.let { t -> UiMessage(t, false) } ?: it.message) }
                    applyTrip(r.value, keepStep = true)
                    after?.invoke()
                }
                is ApiResult.Failure -> fail(r.error)
            }
        }
    }



    internal fun reloadQuietly() {
        scope.launch {
            (repo.getTrip(tripId) as? ApiResult.Success)?.let { applyTrip(it.value, keepStep = true) }
        }
    }

    internal fun fail(e: ApiError) {
        local.update { it.copy(busy = false, message = UiMessage(describe(e), true)) }
    }


}


internal fun countsText(c: Counts?, trip: Trip?): String =
    c?.entries?.joinToString(" · ") { (k, v) -> "${trip?.kindNames?.get(k) ?: k} $v" } ?: "-"

internal fun issueLabel(kind: String) = when (kind) {
    "shift" -> "적재물 이동 의심"
    "fall" -> "넘어짐 의심"
    "leak" -> "누액 의심"
    else -> "적재 이상 의심"
}

internal fun mismatchSpeech(c: Comparison, trip: Trip, label: String): String {
    val d = c.differences.firstOrNull()
    return if (d != null && d.observed > d.expected) {
        "$label 에서 ${trip.kindNames[d.kind] ?: d.kind} ${d.observed - d.expected}개가 더 남아 있는 것으로 보입니다. 확인해 주세요."
    } else "관측과 예상이 다릅니다. 확인해 주세요."
}

