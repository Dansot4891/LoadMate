package com.loadmate.app.core.pleos

/** 마지막 목적지 전달 요청의 결과 (기획서 16장: 요청 실패를 길 안내 시작으로 표시하지 않음) */
sealed interface RouteRequestStatus {
    data object None : RouteRequestStatus
    data object Waiting : RouteRequestStatus
    data class Started(val name: String, val distanceMeters: Int, val durationSeconds: Int) : RouteRequestStatus
    data class Failed(val reason: String) : RouteRequestStatus
    data object Cancelled : RouteRequestStatus
}

/**
 * NaviState.events(최신이 앞)에서 마지막 요청 이후 결과를 읽는다.
 * 실측(2026-11-08): 요청 직후 기존 경로의 onRouteCancelled 가 먼저 오고, 성공 시 onRouteStarted 직후 onRouteEnded 가 붙어 온다
 * → 요청 직후의 취소·종료는 무시하고, 안내 시작 이후의 취소만 "취소됨"으로 본다.
 */
internal fun routeRequestStatus(eventsNewestFirst: List<NaviEvent>): RouteRequestStatus {
    val idx = eventsNewestFirst.indexOfFirst { it is NaviEvent.RouteRequested }
    if (idx < 0) {
        // 요청이 앱 쪽에서 막힌 경우(미연결 등) RouteRequested 없이 RequestFailed 만 남는다
        val failed = eventsNewestFirst.firstOrNull { it is NaviEvent.RequestFailed } as NaviEvent.RequestFailed?
        return failed?.let { RouteRequestStatus.Failed(it.reason) } ?: RouteRequestStatus.None
    }
    var status: RouteRequestStatus = RouteRequestStatus.Waiting
    for (e in eventsNewestFirst.subList(0, idx).asReversed()) {
        status = when (e) {
            is NaviEvent.RouteStarted -> RouteRequestStatus.Started(e.name, e.distanceMeters, e.durationSeconds)
            is NaviEvent.Error -> RouteRequestStatus.Failed(e.code)
            is NaviEvent.RequestFailed -> RouteRequestStatus.Failed(e.reason)
            is NaviEvent.RouteCancelled -> if (status is RouteRequestStatus.Started) RouteRequestStatus.Cancelled else status
            else -> status
        }
    }
    return status
}

