package com.loadmate.app.core.pleos

import kotlinx.coroutines.flow.StateFlow

/**
 * 내비게이션 연동 경계. 화면·도메인은 이 인터페이스만 본다 (Pleos SDK 타입 노출 금지).
 * 구현: [PleosNaviGateway] (NaviHelper 3.0.5).
 */
interface NaviGateway {
    val state: StateFlow<NaviState>

    /** 내비 서비스 연결. 이미 연결 중이거나 연결됐으면 무시. */
    fun connect()

    /**
     * 목적지 전달(F16). 반환은 "요청 전송"까지만 의미한다.
     * 실제 경로 시작 여부는 [NaviEvent.RouteStarted] / [NaviEvent.Error] 로 확인한다.
     */
    fun requestRoute(destination: NaviDestination)

    fun cancelRoute()

    /** 리스너 해제 후 서비스 연결 해제. */
    fun release()
}

data class NaviDestination(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
)

sealed interface NaviConnection {
    data object Idle : NaviConnection
    data object Connecting : NaviConnection
    data object Connected : NaviConnection
    data class Failed(val reason: String) : NaviConnection
}

/** NaviHelper 콜백을 앱 용어로 옮긴 이벤트. 거리 m, 시간 초 (SDK 값 그대로). */
sealed interface NaviEvent {
    val atMillis: Long

    data class RouteRequested(val destinationName: String, override val atMillis: Long) : NaviEvent
    data class RequestFailed(val reason: String, override val atMillis: Long) : NaviEvent
    data class RouteStarted(val name: String, val distanceMeters: Int, val durationSeconds: Int, override val atMillis: Long) : NaviEvent
    data class RoutingCompleted(override val atMillis: Long) : NaviEvent
    data class RouteCancelled(override val atMillis: Long) : NaviEvent
    data class RouteEnded(override val atMillis: Long) : NaviEvent
    data class DestinationArrived(val name: String, override val atMillis: Long) : NaviEvent
    data class StatusChanged(val status: String, override val atMillis: Long) : NaviEvent
    /** SDK NaviErrorCode 이름 그대로 (예: CAUSE_SEARCH_ROUTE) */
    data class Error(val code: String, override val atMillis: Long) : NaviEvent
}

data class NaviState(
    val connection: NaviConnection = NaviConnection.Idle,
    /** 최근 이벤트 (최신이 앞). 실측·화면 표시용으로 최대 [MAX_EVENTS] 개 유지 */
    val events: List<NaviEvent> = emptyList(),
) {
    companion object {
        const val MAX_EVENTS = 20
    }
}
