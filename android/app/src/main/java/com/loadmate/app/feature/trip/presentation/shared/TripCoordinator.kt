package com.loadmate.app.feature.trip.presentation.shared

import com.loadmate.app.feature.trip.presentation.cargo.acceptUnitPosition
import com.loadmate.app.feature.trip.presentation.cargo.restoreUnitPosition
import com.loadmate.app.feature.trip.presentation.overview.confirmDeparture
import com.loadmate.app.feature.trip.presentation.driving.completeUnload
import com.loadmate.app.feature.trip.presentation.cargo.submitPickup
import com.loadmate.app.feature.trip.presentation.driving.sendRoute
import com.loadmate.app.feature.trip.presentation.driving.askRoute
import com.loadmate.app.feature.trip.presentation.overview.checkAddress
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
import com.loadmate.app.feature.trip.domain.UnloadSignal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    doorWarningAt: StateFlow<Long?> = MutableStateFlow(null),
    unloadSignal: StateFlow<UnloadSignal> = MutableStateFlow(UnloadSignal()),
    private val setUnloadAsk: (String?) -> Unit = {},
    internal val appVisible: StateFlow<Boolean> = MutableStateFlow(true),
) {

    fun release() {
        rearDoor.stop()
        scope.cancel()
    }

    internal val local = MutableStateFlow(TripUiState())

    val state: StateFlow<TripUiState> =
        combine(local, navi.state, rearDoor.state, doorWarningAt, unloadSignal) { s, n, d, warnedAt, unload ->
            s.copy(naviConnected = n.connection == NaviConnection.Connected, routeStatus = routeRequestStatus(n.events), rearDoor = d, doorWarningAt = warnedAt, arrivedStopId = unload.arrivedStopId)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), TripUiState())

    // 같은 요청의 재시도는 같은 키로 보낸다 (중복 반영 방지). 반영 확정 후에만 비운다.
    private val keys = mutableMapOf<String, String>()
    internal fun keyFor(op: String) = keys.getOrPut(op) { newKey() }
    internal fun done(op: String) { keys.remove(op) }

    init {
        refresh()
        // 뒷문이 닫히면 서버 판정부터 받는다. 문제가 없으면 기사에게 묻지 않는다
        scope.launch {
            unloadSignal.map { it.askStopId }.distinctUntilChanged().filterNotNull().collect { stopId ->
                setUnloadAsk(null)
                val s = local.first { it.trip != null && !it.busy }
                if (s.trip?.currentStop?.id == stopId) completeUnload(driverConfirmed = false)
            }
        }
        scope.launch { repo.currentTrip.collect { trip -> if (trip != null && trip.id == tripId && trip != local.value.trip) applyTrip(trip) } }
    }

    fun onAction(a: TripUiAction) {
        when (a) {
            TripUiAction.Refresh -> refresh()
            TripUiAction.RefreshVehicleNotes -> refreshVehicleNotes()
            TripUiAction.AskDeparture -> {
                local.update { it.copy(departureDialog = true) }
                if (local.value.trip?.let(::routeLoadGap) != null) speech?.speak(ROUTE_LOAD_GAP_SPEECH)
            }
            TripUiAction.DismissDeparture -> local.update { it.copy(departureDialog = false) }
            TripUiAction.ConfirmDeparture -> confirmDeparture()
            TripUiAction.AskRoute -> askRoute()
            TripUiAction.ConfirmRoute -> sendRoute()
            TripUiAction.DismissRoute -> local.update { it.copy(routeDialog = null) }
            TripUiAction.DismissUnload -> local.update { it.copy(unloadAskStopId = null) }
            TripUiAction.ConfirmUnload -> {
                local.update { it.copy(unloadAskStopId = null) }
                completeUnload(driverConfirmed = true)
            }
            TripUiAction.PassUnloadMismatch -> completeUnload(driverConfirmed = true)
            TripUiAction.DismissUnloadMismatch -> local.update { it.copy(pendingConfirmation = null) }
            TripUiAction.LoadStopNotes -> loadStopNotes()
            TripUiAction.DismissMessage -> local.update { it.copy(message = null) }
            is TripUiAction.CheckAddress -> checkAddress(a.address)
            TripUiAction.ClearAddressCheck -> local.update { it.copy(addressCheck = AddressCheckState()) }
            is TripUiAction.AddStop -> change("addStop:${a.name}:${a.address}:${a.qty}", "${a.name} 추가했습니다") { t, k ->
                repo.addStop(t.id, k, a.name, a.address, null, null, listOf(NewCargo("박스", "box", a.qty)))
            }
            is TripUiAction.EditStopPlace -> change("place:${a.stopId}:${a.name}:${a.address}", "수정했습니다. 안내 시작을 눌러 주세요") { t, k ->
                repo.changeStopPlace(t.id, a.stopId, k, a.name, a.address)
            }
            is TripUiAction.ReorderStops -> change("reorder:${a.stopIds.joinToString(",")}", "순서를 바꿨습니다. 안내 시작을 눌러 주세요") { t, k -> repo.reorderStops(t.id, k, a.stopIds) }
            is TripUiAction.CancelStop -> change("cancel:${a.stopId}", "경로에서 삭제했습니다") { t, k -> repo.cancelStop(t.id, a.stopId, k, a.reason) }
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
            is TripUiAction.ReturnPickup -> change("return:${a.pickupId}", "반납했습니다") { t, k -> repo.returnPickup(t.id, a.pickupId, k) }
            is TripUiAction.ResolveIssue -> change("resolve:${a.issueId}", "확인했습니다") { t, k -> repo.resolveIssue(t.id, a.issueId, k, a.resolution, null) }
            is TripUiAction.AcceptUnitPosition -> acceptUnitPosition(a.unitId)
            is TripUiAction.RestoreUnitPosition -> restoreUnitPosition(a.unitId)
        }
    }

    // ------------------------------------------------------------------ 서버

    /** 도착한 방문지의 장소 메모와 차량 노트를 적재 화면에 보여 주려고 받아 온다 */
    private fun loadStopNotes() {
        val trip = local.value.trip ?: return
        val stop = trip.currentStop ?: return
        local.update { it.copy(stopMemos = emptyList()) }
        refreshVehicleNotes()
        val repository = memos ?: return
        scope.launch {
            (repository.forStop(trip.id, stop.id) as? ApiResult.Success)?.let { r -> local.update { it.copy(stopMemos = r.value) } }
        }
    }

    private fun refreshVehicleNotes() {
        val vehicleId = local.value.trip?.vehicleId ?: return
        val repository = memos ?: return
        scope.launch {
            (repository.list("vehicle", vehicleId, null, null) as? ApiResult.Success)?.let { r -> local.update { it.copy(vehicleNotes = r.value) } }
        }
    }

    internal fun refresh() {
        local.update { it.copy(loading = true, loadError = null) }
        scope.launch {
            when (val r = repo.getTrip(tripId)) {
                is ApiResult.Success -> applyTrip(r.value)
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

    internal fun applyTrip(trip: Trip) {
        local.update { s ->
            val step = when {
                trip.isEnded -> TripStep.DONE
                trip.baseline == null -> TripStep.OVERVIEW
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
                    applyTrip(r.value)
                    after?.invoke()
                }
                is ApiResult.Failure -> fail(r.error)
            }
        }
    }



    internal fun reloadQuietly() {
        scope.launch {
            (repo.getTrip(tripId) as? ApiResult.Success)?.let { applyTrip(it.value) }
        }
    }

    internal fun fail(e: ApiError) {
        local.update { it.copy(busy = false, message = UiMessage(describe(e), true)) }
    }


}


internal const val ROUTE_LOAD_GAP_SPEECH = "경로 짐과 실린 짐이 다릅니다. 확인해 주세요."

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
        "$label ${trip.kindNames[d.kind] ?: d.kind} ${d.observed - d.expected}개가 더 보입니다. 확인해 주세요."
    } else "수량이 다릅니다. 확인해 주세요."
}

