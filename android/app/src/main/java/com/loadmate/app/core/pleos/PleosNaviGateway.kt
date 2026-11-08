package com.loadmate.app.core.pleos

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * NaviHelper 3.0.5 연결.
 * - 권한: requestRoute·cancelRoute → `NAVI_ROUTE_SEARCH` (공식 문서, docs/planning-review.md §3)
 * - 결과는 NaviHelperEventListener 콜백으로만 확인 (호출 반환 ≠ 경로 시작)
 * - 참고 구현: PleosLab othersdk/OtherSdks.kt (같은 작성자 코드). 공식 샘플은 LICENSE 없음 → 복사하지 않음.
 */
class PleosNaviGateway(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : NaviGateway {

    private val appContext = context.applicationContext
    private var helper: NaviHelper? = null

    private val _state = MutableStateFlow(NaviState())
    override val state: StateFlow<NaviState> = _state.asStateFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) =
            emit(NaviEvent.RouteStarted(info.name, info.distance, info.duration, clock()))

        override fun onRoutingCompleted() = emit(NaviEvent.RoutingCompleted(clock()))
        override fun onRouteCancelled() = emit(NaviEvent.RouteCancelled(clock()))
        override fun onRouteEnded() = emit(NaviEvent.RouteEnded(clock()))

        override fun onDestinationArrived(info: DestinationArrivedInfo) =
            emit(NaviEvent.DestinationArrived(info.name, clock()))

        override fun onNavigationStatus(status: NavigationStatus) =
            emit(NaviEvent.StatusChanged(status.name, clock()))

        override fun onError(errorCode: NaviErrorCode) = emit(NaviEvent.Error(errorCode.name, clock()))
    }

    override fun connect() {
        if (helper != null) return
        _state.update { it.copy(connection = NaviConnection.Connecting) }
        try {
            val h = NaviHelper(appContext)
            helper = h
            h.initialize(
                onConnected = {
                    h.addListener(listener)
                    _state.update { it.copy(connection = NaviConnection.Connected) }
                },
                onFailed = { t ->
                    helper = null
                    _state.update { it.copy(connection = NaviConnection.Failed(t.toString())) }
                },
            )
        } catch (t: Throwable) {
            helper = null
            _state.update { it.copy(connection = NaviConnection.Failed(t.toString())) }
        }
    }

    override fun requestRoute(destination: NaviDestination, waypoints: List<NaviDestination>) {
        val h = connectedHelper() ?: return
        if (waypoints.size > NaviGateway.MAX_WAYPOINTS) {
            emit(NaviEvent.RequestFailed("경유지는 ${NaviGateway.MAX_WAYPOINTS}개까지 전달할 수 있습니다", clock()))
            return
        }
        try {
            val dest = RouteInfo(
                latitude = destination.latitude,
                longitude = destination.longitude,
                poiName = destination.name,
                poiId = "",
                address = destination.address,
                poiSubId = "",
            )
            if (waypoints.isEmpty()) {
                h.requestRoute(dest)
            } else {
                val idx = listOf(WaypointIndex.FIRST, WaypointIndex.SECOND)
                h.requestRouteWithWaypoints(
                    RouteWithWaypointsRequest(
                        destination = dest,
                        waypoints = waypoints.mapIndexed { i, w ->
                            RequestWaypointInfo(w.latitude, w.longitude, w.name, idx[i], "", w.address, "", "")
                        },
                    ),
                )
            }
            val names = (waypoints + destination).joinToString(" → ") { it.name }
            emit(NaviEvent.RouteRequested(names, clock()))
        } catch (t: Throwable) {
            // SecurityException(권한 누락) 등. 조용히 넘기지 않고 이벤트로 남긴다.
            emit(NaviEvent.RequestFailed(t.toString(), clock()))
        }
    }

    override fun cancelRoute() {
        val h = connectedHelper() ?: return
        runCatching { h.cancelRoute() }.onFailure { emit(NaviEvent.RequestFailed(it.toString(), clock())) }
    }

    override fun release() {
        val h = helper ?: return
        runCatching { h.removeListener(listener) }
        runCatching { h.release() }
        helper = null
        _state.update { it.copy(connection = NaviConnection.Idle) }
    }

    private fun connectedHelper(): NaviHelper? {
        val h = helper
        if (h == null || _state.value.connection != NaviConnection.Connected) {
            emit(NaviEvent.RequestFailed("내비 서비스 미연결", clock()))
            return null
        }
        return h
    }

    private fun emit(event: NaviEvent) {
        Log.i(TAG, event.toString())
        _state.update { it.copy(events = (listOf(event) + it.events).take(NaviState.MAX_EVENTS)) }
    }

    private companion object {
        const val TAG = "LoadmateNavi"
    }
}
