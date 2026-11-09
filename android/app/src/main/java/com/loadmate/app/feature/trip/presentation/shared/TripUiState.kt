package com.loadmate.app.feature.trip.presentation.shared

import com.loadmate.app.core.pleos.RouteRequestStatus
import com.loadmate.app.core.pleos.RearDoorState
import com.loadmate.app.feature.records.domain.Memo
import com.loadmate.app.feature.trip.domain.Comparison
import com.loadmate.app.feature.trip.domain.Counts
import com.loadmate.app.feature.trip.domain.Trip
import java.time.Instant

/** 화면 단계. 기획서 11장 화면 묶음 */
enum class TripStep { OVERVIEW /* S01 */, DRIVING /* S02·S03 */, DONE }

/** 회수 입력 (F44). 음성 초안도 같은 형식으로 채운다 */
data class PickupDraft(val itemName: String = "빈 용기", val kind: String = "container", val qty: Int = 1, val returnPlace: String = "창고")

/** 배송지 직접 입력의 위치 확인. 확인한 주소 문자열과 지금 입력값이 같을 때만 그 결과를 쓴다 */
data class AddressCheckState(val checking: Boolean = false, val foundAddress: String? = null, val notFoundAddress: String? = null, val error: String? = null)

data class TripUiState(
    val loading: Boolean = true,
    val trip: Trip? = null,
    val loadError: String? = null,
    /** 서버에 닿지 못해 마지막으로 받은 값을 보여 주는 중이면 그 수신 시각 (R11·F66) */
    val offlineSince: Instant? = null,
    val step: TripStep = TripStep.OVERVIEW,
    val busy: Boolean = false,
    /** 상차 시뮬레이션 재생 중 */
    val message: UiMessage? = null,
    val lastComparison: Comparison? = null,
    // 도착한 방문지와 하차 확인 팝업(서버가 판정하지 못한 방문지). pendingConfirmation 은 서버가 수량이 다르다고 답한 비교 결과
    val arrivedStopId: String? = null,
    val unloadAskStopId: String? = null,
    val pendingConfirmation: Comparison? = null,
    // 회수 입력
    val pickupDraft: PickupDraft? = null,
    // 운행 종료 확인
    val endDialog: Boolean = false,
    // 현장 관련 메모 (F55)
    val stopMemos: List<Memo> = emptyList(),
    // 차량 노트 (운행 시작·도착 카드)
    val vehicleNotes: List<Memo> = emptyList(),
    // 출발 전 적재 확인 팝업
    val departureDialog: Boolean = false,
    val addressCheck: AddressCheckState = AddressCheckState(),
    // 내비
    val naviConnected: Boolean = false,
    val routeDialog: RoutePlan? = null,
    val routeStatus: RouteRequestStatus = RouteRequestStatus.None,
    val lastRoutePlan: RoutePlan? = null,
    // 뒷문 (에뮬레이터 신호)
    val rearDoor: RearDoorState = RearDoorState(),
    /** 이번 방문지로 가는 동안 뒷문 경고를 한 시각 (이번 실행 중에만 보관) */
    val doorWarningAt: Long? = null,
)

data class UiMessage(val text: String, val isError: Boolean)
