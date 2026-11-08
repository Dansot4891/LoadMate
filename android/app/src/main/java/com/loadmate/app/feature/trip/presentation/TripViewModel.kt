package com.loadmate.app.feature.trip.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.loadmate.app.AppContainer
import com.loadmate.app.core.pleos.NaviConnection
import com.loadmate.app.core.pleos.NaviGateway
import com.loadmate.app.core.pleos.RearDoorGateway
import com.loadmate.app.core.pleos.RearDoorState
import com.loadmate.app.feature.trip.domain.ApiError
import com.loadmate.app.feature.trip.domain.ApiResult
import com.loadmate.app.feature.trip.domain.Comparison
import com.loadmate.app.feature.trip.domain.Counts
import com.loadmate.app.feature.trip.domain.ObservationSource
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.domain.TripRepository
import com.loadmate.app.feature.trip.domain.TripStatus
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

data class TripUiState(
    val loading: Boolean = true,
    val trip: Trip? = null,
    val loadError: String? = null,
    val step: TripStep = TripStep.OVERVIEW,
    val busy: Boolean = false,
    /** 짧은 안내 (성공·실패). 닫기 전까지 표시 */
    val message: UiMessage? = null,
    // 관측 입력 (카메라 API 없음 → 샘플·기사 입력)
    val observationDraft: Counts = emptyMap(),
    val observationSource: ObservationSource = ObservationSource.SAMPLE,
    val lastComparison: Comparison? = null,
    // 하차 완료 요청
    val deliveredDraft: Counts = emptyMap(),
    /** S06 에 보여 줄 확인 필요 근거 */
    val pendingConfirmation: Comparison? = null,
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
    /** S06: 남은 물품을 찾아 내림 → 다시 관측 */
    data object RecheckAfterUnload : TripUiAction
    /** S06: 기사 확인으로 완료 (R07) */
    data object DriverConfirmComplete : TripUiAction
    data object DismissMessage : TripUiAction
    data object SiteVisible : TripUiAction
    data object SiteHidden : TripUiAction
}

/**
 * 대표 시나리오(기획서 14장) 진행. 잔량·완료 판정은 서버 결과만 사용한다 (원칙 2).
 * 내비 리스너는 운행 중 유지하고 운행 완료·화면 종료 시 해제 (원칙 8, 2026-11-08 결정).
 */
class TripViewModel(
    private val repo: TripRepository,
    private val navi: NaviGateway,
    private val rearDoor: RearDoorGateway,
    private val tripId: String,
    private val now: () -> Instant = Instant::now,
    private val newKey: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val local = MutableStateFlow(TripUiState())

    val state: StateFlow<TripUiState> =
        combine(local, navi.state, rearDoor.state) { s, n, d ->
            s.copy(
                naviConnected = n.connection == NaviConnection.Connected,
                routeStatus = routeRequestStatus(n.events),
                rearDoor = d,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripUiState())

    // 같은 요청의 재시도는 같은 키로 보낸다 (중복 반영 방지). 반영 확정 후에만 비운다.
    private var baselineKey: String? = null
    private var completeKey: Pair<String, String>? = null // stopId → key
    private var observationKey: String? = null

    init {
        refresh()
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
        }
    }

    // ------------------------------------------------------------------ 서버

    private fun refresh() {
        local.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            when (val r = repo.getTrip(tripId)) {
                is ApiResult.Success -> applyTrip(r.value, keepStep = true)
                is ApiResult.Failure -> local.update { it.copy(loading = false, loadError = describe(r.error)) }
            }
        }
    }

    private fun applyTrip(trip: Trip, keepStep: Boolean) {
        local.update { s ->
            val step = when {
                trip.status == TripStatus.COMPLETED -> TripStep.DONE
                trip.baseline == null -> if (keepStep && s.step == TripStep.LOAD_CHECK) TripStep.LOAD_CHECK else TripStep.OVERVIEW
                keepStep && s.step in setOf(TripStep.SITE_WORK, TripStep.MISMATCH) -> s.step
                else -> TripStep.DRIVING
            }
            s.copy(loading = false, loadError = null, trip = trip, step = step, lastComparison = trip.comparison)
        }
        // 운행 중에는 내비 연결 유지, 완료되면 해제
        if (trip.baseline != null && trip.status != TripStatus.COMPLETED) navi.connect()
        if (trip.status == TripStatus.COMPLETED) navi.release()
    }

    private fun recordObservation(unavailable: Boolean) {
        val s = local.value
        val trip = s.trip ?: return
        if (s.busy) return
        val key = observationKey ?: newKey().also { observationKey = it }
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            val counts = if (unavailable) null else s.observationDraft
            val at = now().truncatedTo(ChronoUnit.SECONDS).toString()
            when (val r = repo.recordObservation(trip.id, key, s.observationSource, counts, at)) {
                is ApiResult.Success -> {
                    observationKey = null
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
        val key = baselineKey ?: newKey().also { baselineKey = it }
        val obsId = s.lastComparison?.observation?.id
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val r = repo.confirmBaseline(trip.id, key, s.observationDraft, obsId)) {
                is ApiResult.Success -> {
                    baselineKey = null
                    local.update { it.copy(busy = false, message = UiMessage("최초 적재 기준 확정 · ${countsText(r.value.baseline?.counts, r.value)}", false)) }
                    applyTrip(r.value, keepStep = false)
                }
                is ApiResult.Failure -> {
                    if ((r.error as? ApiError.Http)?.code == "baseline_already_confirmed") baselineKey = null
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
            )
        }
    }

    private fun complete(driverConfirmed: Boolean) {
        val s = local.value
        val trip = s.trip ?: return
        val stop = trip.currentStop ?: return
        if (s.busy) return
        val key = completeKey?.takeIf { it.first == stop.id }?.second ?: newKey().also { completeKey = stop.id to it }
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val r = repo.completeStop(trip.id, stop.id, key, s.deliveredDraft, driverConfirmed)) {
                is ApiResult.Success -> {
                    val res = r.value
                    if (res.completed) {
                        completeKey = null
                        val how = if (res.stop.resolution == "driver_confirmed") "기사 확인으로 완료" else "관측과 일치해 완료"
                        local.update {
                            it.copy(busy = false, pendingConfirmation = null, step = TripStep.DRIVING,
                                message = UiMessage("${res.stop.label} 하차 완료 · $how · 예상 잔량 ${countsText(res.expectedRemaining, trip)}", false))
                        }
                        reloadQuietly()
                    } else {
                        // 저장되지 않음. 같은 키로 다시 보낼 수 있다
                        local.update { it.copy(busy = false, step = TripStep.MISMATCH, pendingConfirmation = res.comparison) }
                    }
                }
                is ApiResult.Failure -> {
                    if ((r.error as? ApiError.Http)?.code == "stop_already_completed") {
                        completeKey = null
                        local.update { it.copy(step = TripStep.DRIVING) }
                        reloadQuietly()
                    }
                    fail(r.error)
                }
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
            initializer { TripViewModel(container.tripRepository, container.naviGateway, container.rearDoorGateway, tripId) }
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
