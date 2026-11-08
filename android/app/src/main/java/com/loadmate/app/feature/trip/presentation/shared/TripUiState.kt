package com.loadmate.app.feature.trip.presentation.shared

import com.loadmate.app.core.pleos.RouteRequestStatus
import com.loadmate.app.core.pleos.RearDoorState
import com.loadmate.app.feature.records.domain.Memo
import com.loadmate.app.feature.trip.domain.Comparison
import com.loadmate.app.feature.trip.domain.Counts
import com.loadmate.app.feature.trip.domain.ObservationSource
import com.loadmate.app.feature.trip.domain.Trip
import java.time.Instant

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

interface ObservationUiState {
    val busy: Boolean
    val observationDraft: Counts
    val observationSource: ObservationSource
    val lastComparison: Comparison?
}

data class UiMessage(val text: String, val isError: Boolean)
