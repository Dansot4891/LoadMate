package com.loadmate.app.feature.trip.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.loadmate.app.AppContainer
import com.loadmate.app.core.notify.DriverNotifier
import com.loadmate.app.core.pleos.NaviConnection
import com.loadmate.app.core.pleos.NaviGateway
import com.loadmate.app.core.pleos.RearDoorGateway
import com.loadmate.app.core.pleos.RearDoorState
import com.loadmate.app.core.pleos.SpeechOutput
import com.loadmate.app.feature.records.domain.Memo
import com.loadmate.app.feature.records.domain.MemoRepository
import com.loadmate.app.feature.trip.domain.ApiError
import com.loadmate.app.feature.trip.domain.ApiResult
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
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** 화면 단계. 기획서 11장 화면 묶음 */
enum class TripStep { OVERVIEW /* S01 */, LOAD_CHECK /* S05 */, DRIVING /* S02·S03 */, SITE_WORK /* S04 */, MISMATCH /* S06 */, DONE }

/** 회수 입력 (F44). 음성 초안도 같은 형식으로 채운다 */
data class PickupDraft(val itemName: String = "빈 용기", val kind: String = "container", val qty: Int = 1, val returnPlace: String = "창고")

data class TripUiState(
    val loading: Boolean = true,
    val trip: Trip? = null,
    val loadError: String? = null,
    /** 서버에 닿지 못해 마지막으로 받은 값을 보여 주는 중이면 그 수신 시각 (R11·F66) */
    val offlineSince: Instant? = null,
    val step: TripStep = TripStep.OVERVIEW,
    val busy: Boolean = false,
    val message: UiMessage? = null,
    // 관측 입력 (카메라 API 없음 → 샘플·기사 입력)
    val observationDraft: Counts = emptyMap(),
    val observationSource: ObservationSource = ObservationSource.SAMPLE,
    val lastComparison: Comparison? = null,
    // 하차 완료 요청
    val deliveredDraft: Counts = emptyMap(),
    val pendingConfirmation: Comparison? = null,
    // 회수 입력
    val pickupDraft: PickupDraft? = null,
    // 운행 종료 확인
    val endDialog: Boolean = false,
    // 현장 관련 메모 (F55)
    val stopMemos: List<Memo> = emptyList(),
    // 내비
    val naviConnected: Boolean = false,
    val routeDialog: RoutePlan? = null,
    val routeStatus: RouteRequestStatus = RouteRequestStatus.None,
    val lastRoutePlan: RoutePlan? = null,
    // 뒷문 (에뮬레이터 신호)
    val rearDoor: RearDoorState = RearDoorState(),
)

data class UiMessage(val text: String, val isError: Boolean)

sealed interface TripUiAction {
    data object Refresh : TripUiAction
    data object OpenLoadCheck : TripUiAction
    data object BackToOverview : TripUiAction
    data class ObservationCount(val kind: String, val value: Int) : TripUiAction
    data class ObservationSourceSelect(val source: ObservationSource) : TripUiAction
    data object RecordObservation : TripUiAction
    data object RecordUnavailable : TripUiAction
    data object ConfirmBaseline : TripUiAction
    data object AskRoute : TripUiAction
    data object ConfirmRoute : TripUiAction
    data object DismissRoute : TripUiAction
    data object Arrived : TripUiAction
    data object BackToDriving : TripUiAction
    data class DeliveredCount(val kind: String, val value: Int) : TripUiAction
    data object RequestComplete : TripUiAction
    data object RecheckAfterUnload : TripUiAction
    data object DriverConfirmComplete : TripUiAction
    data object DismissMessage : TripUiAction
    data object SiteVisible : TripUiAction
    data object SiteHidden : TripUiAction
    // 운행 관리 (F04·F18·F29·F07)
    data class AddSampleStop(val index: Int) : TripUiAction
    data class MoveStop(val stopId: String, val up: Boolean) : TripUiAction
    data class CancelStop(val stopId: String, val reason: String) : TripUiAction
    data class ChangeQty(val stopId: String, val cargoLineId: Int, val qty: Int) : TripUiAction
    data object AskEndTrip : TripUiAction
    data object DismissEndTrip : TripUiAction
    data class EndTrip(val reason: String?) : TripUiAction
    // 회수 (F44~F46). fromMismatch: S06 "회수품이에요" 분기
    data class OpenPickup(val draft: PickupDraft = PickupDraft()) : TripUiAction
    data class EditPickup(val draft: PickupDraft) : TripUiAction
    data object SubmitPickup : TripUiAction
    data object DismissPickup : TripUiAction
    data class ReturnPickup(val pickupId: Int) : TripUiAction
    // 적재 이상 (F30·F31·F34). 카메라가 없어 샘플 관측으로 기록
    data class RecordSampleIssue(val kind: String) : TripUiAction
    data class ResolveIssue(val issueId: Int, val resolution: String) : TripUiAction
}

/**
 * 대표 시나리오(14장)와 운행 관리. 잔량·완료 판정은 서버 결과만 사용한다 (원칙 2).
 * 내비 리스너는 운행 중 유지하고 운행 완료·화면 종료 시 해제 (원칙 8).
 * 주행 중에는 화면을 바꾸지 않고 짧은 음성·알림만 쓴다 (R09).
 */
class TripViewModel(
    private val repo: TripRepository,
    private val memos: MemoRepository?,
    private val navi: NaviGateway,
    private val rearDoor: RearDoorGateway,
    private val speech: SpeechOutput?,
    private val notify: (id: Int, title: String, text: String) -> Unit,
    private val cached: (String) -> Pair<Trip, Instant>?,
    externalChanges: Flow<Unit>,
    private val tripId: String,
    private val now: () -> Instant = Instant::now,
    private val newKey: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val local = MutableStateFlow(TripUiState())

    val state: StateFlow<TripUiState> =
        combine(local, navi.state, rearDoor.state) { s, n, d ->
            s.copy(naviConnected = n.connection == NaviConnection.Connected, routeStatus = routeRequestStatus(n.events), rearDoor = d)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripUiState())

    // 같은 요청의 재시도는 같은 키로 보낸다 (중복 반영 방지). 반영 확정 후에만 비운다.
    private val keys = mutableMapOf<String, String>()
    private fun keyFor(op: String) = keys.getOrPut(op) { newKey() }
    private fun done(op: String) { keys.remove(op) }

    private var lastAnnouncedRoute: RouteRequestStatus = RouteRequestStatus.None

    init {
        refresh()
        viewModelScope.launch { externalChanges.collect { reloadQuietly() } }
        // 내비 결과를 짧게 알린다. 내비가 화면을 덮고 있어도 들리고 보이게 (실측: 헤드업·TTS 동작)
        viewModelScope.launch {
            navi.state.collect { n ->
                val st = routeRequestStatus(n.events)
                if (st != lastAnnouncedRoute) {
                    when (st) {
                        is RouteRequestStatus.Started -> speech?.speak("${st.name}으로 안내를 시작했습니다.")
                        is RouteRequestStatus.Failed -> {
                            speech?.speak("내비 안내 요청이 실패했습니다. 앱에서 다시 보내 주세요.")
                            notify(DriverNotifier.ID_ROUTE, "내비 안내 실패", "${st.reason} · Loadmate 에서 다시 보내기")
                        }
                        else -> Unit
                    }
                    lastAnnouncedRoute = st
                }
            }
        }
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

    private fun refresh() {
        local.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
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

    private fun applyTrip(trip: Trip, keepStep: Boolean) {
        local.update { s ->
            val step = when {
                trip.isEnded -> TripStep.DONE
                trip.baseline == null -> if (keepStep && s.step == TripStep.LOAD_CHECK) TripStep.LOAD_CHECK else TripStep.OVERVIEW
                keepStep && s.step in setOf(TripStep.SITE_WORK, TripStep.MISMATCH) && trip.currentStop?.id == s.trip?.currentStop?.id -> s.step
                else -> TripStep.DRIVING
            }
            s.copy(loading = false, loadError = null, offlineSince = null, trip = trip, step = step, lastComparison = trip.comparison)
        }
        if (trip.baseline != null && !trip.isEnded) navi.connect()
        if (trip.isEnded) navi.release()
    }

    private fun recordObservation(unavailable: Boolean) {
        val s = local.value
        val trip = s.trip ?: return
        if (s.busy) return
        val key = keyFor("observation")
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            val counts = if (unavailable) null else s.observationDraft
            val at = now().truncatedTo(ChronoUnit.SECONDS).toString()
            val zones = if (unavailable || s.observationSource != ObservationSource.SAMPLE) null else {
                val delivered = if (s.step == TripStep.SITE_WORK) s.deliveredDraft else emptyMap()
                sampleZones(trip, s.observationDraft, trip.currentStop?.id.takeIf { s.step == TripStep.SITE_WORK }, delivered)
            }
            when (val r = repo.recordObservation(trip.id, key, s.observationSource, counts, at, zones)) {
                is ApiResult.Success -> {
                    done("observation")
                    local.update { it.copy(busy = false, lastComparison = r.value, message = UiMessage("관측 기록됨 · ${s.observationSource.label}", false)) }
                    reloadQuietly()
                }
                is ApiResult.Failure -> fail(r.error)
            }
        }
    }

    private fun confirmBaseline() {
        val s = local.value
        val trip = s.trip ?: return
        if (s.busy) return
        val key = keyFor("baseline")
        val obsId = s.lastComparison?.observation?.id
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val r = repo.confirmBaseline(trip.id, key, s.observationDraft, obsId)) {
                is ApiResult.Success -> {
                    done("baseline")
                    local.update { it.copy(busy = false, message = UiMessage("최초 적재 기준 확정 · ${countsText(r.value.baseline?.counts, r.value)}", false)) }
                    applyTrip(r.value, keepStep = false)
                }
                is ApiResult.Failure -> {
                    if ((r.error as? ApiError.Http)?.code == "baseline_already_confirmed") done("baseline")
                    fail(r.error)
                    reloadQuietly()
                }
            }
        }
    }

    private fun enterSite() {
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
            viewModelScope.launch {
                (repo.forStop(trip.id, stop.id) as? ApiResult.Success)?.let { r -> local.update { it.copy(stopMemos = r.value) } }
            }
        }
    }

    private fun complete(driverConfirmed: Boolean) {
        val s = local.value
        val trip = s.trip ?: return
        val stop = trip.currentStop ?: return
        if (s.busy) return
        val op = "complete:${stop.id}"
        val key = keyFor(op)
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val r = repo.completeStop(trip.id, stop.id, key, s.deliveredDraft, driverConfirmed)) {
                is ApiResult.Success -> {
                    val res = r.value
                    if (res.completed) {
                        done(op)
                        val how = if (res.stop.resolution == "driver_confirmed") "기사 확인으로 완료" else "관측과 일치해 완료"
                        local.update {
                            it.copy(busy = false, pendingConfirmation = null, step = TripStep.DRIVING,
                                message = UiMessage("${res.stop.label} 하차 완료 · $how · 예상 잔량 ${countsText(res.expectedRemaining, trip)}", false))
                        }
                        reloadQuietly()
                    } else {
                        // 저장되지 않음. 같은 키로 다시 보낼 수 있다. 짧은 음성으로 의심 안내 (F25·R09)
                        local.update { it.copy(busy = false, step = TripStep.MISMATCH, pendingConfirmation = res.comparison) }
                        speech?.speak(mismatchSpeech(res.comparison, trip, stop.label))
                    }
                }
                is ApiResult.Failure -> {
                    if ((r.error as? ApiError.Http)?.code == "stop_already_completed") {
                        done(op)
                        local.update { it.copy(step = TripStep.DRIVING) }
                        reloadQuietly()
                    }
                    fail(r.error)
                }
            }
        }
    }

    private fun moveStop(stopId: String, up: Boolean) {
        val trip = local.value.trip ?: return
        val ids = trip.pendingStops.map { it.id }.toMutableList()
        val i = ids.indexOf(stopId)
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in ids.indices) return
        ids[i] = ids[j].also { ids[j] = ids[i] }
        change("reorder:${ids.joinToString(",")}", "방문 순서 변경 · 내비에 다시 보내야 반영됩니다") { t, k -> repo.reorderStops(t.id, k, ids) }
    }

    private fun submitPickup() {
        val d = local.value.pickupDraft ?: return
        val trip = local.value.trip ?: return
        val stopId = trip.currentStop?.id
        val fromMismatch = local.value.step == TripStep.MISMATCH
        val text = "회수 기록 · ${d.itemName} ${d.qty}개 → ${d.returnPlace}" + if (fromMismatch) " · 예상 잔량에 반영됐습니다. 다시 완료 요청하세요" else ""
        change("pickup:$stopId:${d.itemName}:${d.qty}:${d.returnPlace}", text, after = {
            // S06 에서 "회수품이에요" 로 왔으면 현장 작업으로 돌아가 같은 요청 ID 로 다시 완료 요청
            if (fromMismatch) local.update { it.copy(step = TripStep.SITE_WORK, pendingConfirmation = null) }
        }) { t, k ->
            repo.addPickup(t.id, k, stopId, d.itemName, d.kind, d.qty, d.returnPlace, "screen")
        }
        local.update { it.copy(pickupDraft = null) }
    }

    private fun recordSampleIssue(kind: String) {
        val trip = local.value.trip ?: return
        val zone = trip.currentStop?.cargo?.firstOrNull()?.zone
        val label = issueLabel(kind)
        val at = now().truncatedTo(ChronoUnit.SECONDS).toString()
        change("issue:$kind:$at", "$label 기록 (샘플 관측)") { t, k -> repo.recordIssue(t.id, k, kind, zone, "샘플 관측", ObservationSource.SAMPLE, at) }
        // 주행 중에는 화면을 바꾸지 않고 짧게 알린다 (R09·F32)
        speech?.speak("$label. 안전한 곳에 정차한 뒤 확인하세요.")
        notify(DriverNotifier.ID_ISSUE, label, "정차 후 Loadmate 에서 확인하세요 (샘플 관측)")
    }

    /** 공통 변경 요청: 같은 op 는 같은 키로 재시도, 성공 시 운행 갱신 */
    private fun change(op: String, okText: String?, after: (() -> Unit)? = null, call: suspend (Trip, String) -> ApiResult<Trip>) {
        val s = local.value
        val trip = s.trip ?: return
        if (s.busy) return
        val key = keyFor(op)
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
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

    private fun sendRoute() {
        val plan = local.value.routeDialog ?: return
        local.update { it.copy(routeDialog = null, lastRoutePlan = plan) }
        navi.connect()
        navi.requestRoute(plan.destination, plan.waypoints)
    }

    private fun reloadQuietly() {
        viewModelScope.launch {
            (repo.getTrip(tripId) as? ApiResult.Success)?.let { applyTrip(it.value, keepStep = true) }
        }
    }

    private fun fail(e: ApiError) {
        local.update { it.copy(busy = false, message = UiMessage(describe(e), true)) }
    }

    override fun onCleared() {
        navi.release()
        rearDoor.stop()
    }

    companion object {
        fun factory(container: AppContainer, tripId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TripViewModel(
                    repo = container.tripRepository, memos = container.memoRepository, navi = container.naviGateway,
                    rearDoor = container.rearDoorGateway, speech = container.speechOutput,
                    notify = { id, title, text -> container.notifier.notify(id, title, text) },
                    cached = { container.tripRepository.cached(it) }, externalChanges = container.tripChanged, tripId = tripId,
                )
            }
        }
    }
}

internal fun describe(e: ApiError): String = when (e) {
    ApiError.NotConfigured -> "서버 주소가 설정되지 않았습니다"
    is ApiError.Network -> "서버에 연결하지 못했습니다 (${e.reason}). 다시 시도하면 같은 요청으로 전송되어 중복 반영되지 않습니다"
    is ApiError.Http -> e.message
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

