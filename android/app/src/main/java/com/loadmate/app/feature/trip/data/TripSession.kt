package com.loadmate.app.feature.trip.data

import com.loadmate.app.core.notify.DriverNotifier
import com.loadmate.app.core.pleos.NaviGateway
import com.loadmate.app.core.pleos.SpeechOutput
import com.loadmate.app.feature.trip.domain.TripRepository
import com.loadmate.app.core.pleos.RouteRequestStatus
import com.loadmate.app.core.pleos.routeRequestStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** 운행 시작부터 종료까지 내비 리스너와 경로 결과 알림을 소유한다. 화면 수명과 무관하다. */
class TripSession(
    private val trips: TripRepository,
    val navi: NaviGateway,
    private val speech: SpeechOutput?,
    private val notify: (id: Int, title: String, text: String) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastAnnounced: RouteRequestStatus = RouteRequestStatus.None
    private var ended = false

    init {
        scope.launch {
            trips.currentTrip.collect { trip ->
                if (trip == null) return@collect
                if (trip.isEnded) {
                    if (!ended) { ended = true; navi.release() }
                } else if (trip.baseline != null) {
                    ended = false
                    navi.connect()
                }
            }
        }
        scope.launch {
            navi.state.collect { state ->
                val status = routeRequestStatus(state.events)
                if (status == lastAnnounced) return@collect
                when (status) {
                    is RouteRequestStatus.Started -> speech?.speak("${status.name}으로 안내를 시작했습니다.")
                    is RouteRequestStatus.Failed -> {
                        speech?.speak("내비 안내 요청이 실패했습니다. 앱에서 다시 보내 주세요.")
                        notify(DriverNotifier.ID_ROUTE, "내비 안내 실패", "${status.reason} · Loadmate 에서 다시 보내기")
                    }
                    else -> Unit
                }
                lastAnnounced = status
            }
        }
    }
}
