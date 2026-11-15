package com.loadmate.app.feature.trip.data

import com.loadmate.app.core.notify.DriverNotifier
import com.loadmate.app.core.notify.TripKeepAlive
import com.loadmate.app.core.pleos.NaviEvent
import com.loadmate.app.core.pleos.NaviGateway
import com.loadmate.app.core.pleos.RearDoorGateway
import com.loadmate.app.core.pleos.RearDoorState
import com.loadmate.app.core.pleos.SpeechOutput
import com.loadmate.app.feature.trip.domain.ComparisonState
import com.loadmate.app.feature.trip.domain.Difference
import com.loadmate.app.feature.trip.domain.ObservationSource
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.domain.TripRepository
import com.loadmate.app.feature.trip.domain.UnloadSignal
import com.loadmate.app.core.pleos.RouteRequestStatus
import com.loadmate.app.core.pleos.routeRequestStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 도착 후 앱이 앞에 나오면 적재 화면과 꺼내는 순서 재생을 여는 요청. 화면 전용이며 서버에 기록하지 않는다 (원칙 2) */
data class ArrivalRequest(val tripId: String, val stopId: String, val atMillis: Long, val autoPlay: Boolean = true)

/**
 * 짐칸 알림의 헤드업 허용 조건. 앱이 뒤에 있을 때만 띄운다. 운전 중에도 위쪽 작은 알림은 내비 화면을 바꾸지 않으므로 띄운다
 * (2026-11-15 사용자 결정. 2026-11-09 에는 정차 중에만 띄웠다). 앱 화면 전환은 기사가 알림을 눌렀을 때만 일어난다.
 */
internal fun cargoPopupAllowed(appVisible: Boolean): Boolean = !appVisible

internal fun cargoIssueSpeech(kind: String, atStop: Boolean): String {
    val what = when (kind) {
        "shift" -> "적재물 이동이"
        "fall" -> "적재물 넘어짐이"
        "leak" -> "누액이"
        else -> "적재 이상이"
    }
    return "짐칸에서 ${what} 의심됩니다. ${checkAdvice(atStop)}"
}

/** 카메라 관측이 예상과 다를 때의 안내. 관측일 뿐이므로 확정하지 않고 확인을 요청한다 (원칙 2) */
internal fun cargoCountSpeech(d: Difference, kindName: String, atStop: Boolean): String =
    "짐칸 ${kindName} 수량이 예상과 다릅니다. 예상 ${d.expected}개, 카메라 ${d.observed}개입니다. ${checkAdvice(atStop)}"

private fun checkAdvice(atStop: Boolean) = if (atStop) "확인해 주세요." else "안전한 곳에 정차한 뒤 확인해 주세요."

/**
 * 운전 중 뒷문 경고 조건. 차량 속도를 알 수 없어(실측) "내비 안내 중이고, 내비가 앞에 있으며, 도착 전"을 운전 중으로 본다.
 * 짐을 내리려고 여는 경우(도착 후·Loadmate 사용 중)는 경고하지 않는다.
 */
internal fun shouldWarnRearDoor(doorOpen: Boolean?, route: RouteRequestStatus, atStop: Boolean, appVisible: Boolean): Boolean =
    doorOpen == true && route is RouteRequestStatus.Started && !atStop && !appVisible

/** 운행 시작부터 종료까지 내비·뒷문 신호와 운행 중 안내를 소유한다. 화면 수명과 무관하다. */
class TripSession(
    private val trips: TripRepository,
    val navi: NaviGateway,
    private val speech: SpeechOutput?,
    private val rearDoor: RearDoorGateway? = null,
    private val keepAlive: TripKeepAlive? = null,
    private val appVisible: StateFlow<Boolean> = MutableStateFlow(false),
    private val clock: () -> Long = System::currentTimeMillis,
    private val cancelNotice: (id: Int) -> Unit = {},
    private val notify: (id: Int, title: String, text: String) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastAnnounced: RouteRequestStatus = RouteRequestStatus.None
    private var ended = false
    private var running = false
    private var lastStopId: String? = null
    private var lastArrivalEvent: NaviEvent? = null
    private var repeatWarning: Job? = null
    private val noticeTimers = mutableMapOf<Int, Job>()
    private var polling: Job? = null
    private var watchedTripId: String? = null
    private var knownIssueIds = emptySet<Int>()
    private var lastCameraObservationId: Int? = null
    private var lastCountDifferences: List<Difference>? = null
    private val arrivedStopId = MutableStateFlow<String?>(null)

    private val _arrival = MutableStateFlow<ArrivalRequest?>(null)
    val arrival: StateFlow<ArrivalRequest?> = _arrival.asStateFlow()

    private val _doorWarningAt = MutableStateFlow<Long?>(null)
    /** 이번 방문지로 가는 동안 뒷문 경고를 한 시각. 다음 방문지로 넘어가면 지운다 */
    val doorWarningAt: StateFlow<Long?> = _doorWarningAt.asStateFlow()

    private val askUnloadStopId = MutableStateFlow<String?>(null)
    /** 도착한 방문지와 하차 확인을 물어볼 방문지. 방문지가 바뀌면 지운다 */
    val unloadSignal: StateFlow<UnloadSignal> =
        combine(arrivedStopId, askUnloadStopId, ::UnloadSignal).stateIn(scope, SharingStarted.Eagerly, UnloadSignal())

    init {
        rearDoor?.let { watchDoorForUnload(it) }
        scope.launch {
            trips.currentTrip.collect { trip ->
                if (trip == null) return@collect
                if (trip.isEnded) {
                    if (!ended) { ended = true; navi.release() }
                } else if (trip.baseline != null) {
                    ended = false
                    navi.connect()
                }
                val active = trip.baseline != null && !trip.isEnded
                if (active != running) {
                    running = active
                    if (active) {
                        rearDoor?.holdForTrip()
                        keepAlive?.start()
                        polling = scope.launch { pollCargoResults() }
                    } else {
                        rearDoor?.releaseTripHold()
                        keepAlive?.stop()
                        polling?.cancel()
                    }
                }
                if (active) watchCargo(trip)
                val stopId = trip.currentStop?.id
                if (stopId != lastStopId) {
                    lastStopId = stopId
                    _doorWarningAt.value = null
                    askUnloadStopId.value = null
                    if (_arrival.value?.stopId != stopId) _arrival.value = null
                }
            }
        }
        scope.launch {
            navi.state.collect { state ->
                val status = routeRequestStatus(state.events)
                // 안내 시작·길 안내 음성은 내비가 맡는다. Loadmate 는 내비가 알려 주지 않는 실패만 말한다.
                if (status != lastAnnounced) {
                    if (status is RouteRequestStatus.Failed) speech?.speak("내비 안내를 시작하지 못했습니다. 앱에서 안내 시작을 눌러 주세요.")
                    lastAnnounced = status
                }
                val arrived = state.events.firstOrNull { it is NaviEvent.DestinationArrived || it is NaviEvent.WaypointArrived }
                if (arrived != null && arrived != lastArrivalEvent) {
                    lastArrivalEvent = arrived
                    // 경유지·목적지 신호가 겹쳐도 방문지당 한 번만. 기사의 "도착했어"는 놓친 팝업을 다시 띄울 수 있게 막지 않는다
                    if (trips.currentTrip.value?.currentStop?.id != arrivedStopId.value) markArrived()
                }
            }
        }
        scope.launch {
            combine(
                trips.currentTrip,
                arrivedStopId,
                navi.state,
                appVisible,
                rearDoor?.state ?: flowOf(RearDoorState()),
            ) { trip, arrivedId, nav, visible, door ->
                val active = trip?.baseline != null && !trip.isEnded
                active && shouldWarnRearDoor(door.isOpen, routeRequestStatus(nav.events), trip?.currentStop?.id == arrivedId, visible)
            }.distinctUntilChanged().collect { warn ->
                repeatWarning?.cancel()
                if (!warn) cancelNotice(DriverNotifier.ID_REAR_DOOR)
                if (warn) {
                    _doorWarningAt.value = clock()
                    speech?.speak(REAR_DOOR_WARNING)
                    // 알림을 누르면 앱이 앞에 나오며 적재 화면이 열린다. 뒷문이 닫히면 알림을 지운다
                    trips.currentTrip.value?.let { t -> t.currentStop?.let { _arrival.value = ArrivalRequest(t.id, it.id, clock(), autoPlay = false) } }
                    drivingNotice(DriverNotifier.ID_REAR_DOOR, "뒷문 열림")
                    repeatWarning = scope.launch {
                        delay(REPEAT_WARNING_MS)
                        speech?.speak(REAR_DOOR_WARNING)
                    }
                }
            }
        }
    }

    /** 도착한 방문지에서 뒷문이 열렸다 닫히면 서버에 하차 판정을 요청한다. 뒷문 신호만으로 완료하지 않는다 (원칙 2) */
    private fun watchDoorForUnload(door: RearDoorGateway) {
        scope.launch {
            var wasOpen = false
            door.state.mapNotNull { it.isOpen }.distinctUntilChanged().collect { open ->
                if (wasOpen && !open) askUnloadAfterDoorClosed()
                wasOpen = open
            }
        }
    }

    private fun askUnloadAfterDoorClosed() {
        val trip = trips.currentTrip.value ?: return
        val stop = trip.currentStop ?: return
        if (trip.baseline == null || trip.isEnded) return
        // 도착 신호를 놓쳤어도 기사가 앱을 보며 뒷문을 여닫았다면 도착한 것으로 본다. 앱이 뒤에 있으면 운전 중일 수 있어 묻지 않는다
        if (stop.id != arrivedStopId.value) {
            if (!appVisible.value) return
            arrivedStopId.value = stop.id
        }
        askUnloadStopId.value = stop.id
    }

    /** 하차 확인 요청을 받아 간 쪽이 지운다 */
    fun setUnloadAsk(stopId: String?) {
        askUnloadStopId.value = stopId
    }

    /**
     * 내비 도착 신호 또는 기사의 "도착했어". 도착 안내 음성은 내비가 하므로 여기서는 말하지 않는다.
     * 앱이 뒤에 있으면 알림을 띄우고, 앱이 앞에 나오는 순간 [arrival] 로 적재 화면을 연다.
     */
    fun markArrived() {
        val trip = trips.currentTrip.value ?: return
        if (trip.baseline == null || trip.isEnded) return
        val stop = trip.currentStop ?: return
        arrivedStopId.value = stop.id
        _arrival.value = ArrivalRequest(trip.id, stop.id, clock())
        if (!appVisible.value) notify(DriverNotifier.ID_ARRIVAL, "${stop.label} 도착", "눌러서 적재 확인")
    }

    fun consumeArrival() {
        _arrival.value = null
    }

    /** 카메라 분석 결과는 서버에 쌓이므로 운행 중 주기적으로 받아 온다. 실패하면 다음 주기에 다시 받는다 */
    private suspend fun pollCargoResults() {
        while (true) {
            delay(CARGO_POLL_MS)
            trips.currentTrip.value?.let { trips.getTrip(it.id) }
        }
    }

    /** 처음 받은 운행의 기존 이상·관측은 새 소식이 아니다. 이후 새로 생긴 카메라 결과만 알린다 */
    private fun watchCargo(trip: Trip) {
        val issueIds = trip.openIssues.map { it.id }.toSet()
        val camera = trip.comparison.observation?.takeIf { it.source == ObservationSource.RECORDED_VIDEO }
        if (trip.id != watchedTripId) {
            watchedTripId = trip.id
            knownIssueIds = issueIds
            lastCameraObservationId = camera?.id
            return
        }
        val atStop = trip.currentStop?.id != null && trip.currentStop?.id == arrivedStopId.value
        val newIssue = trip.openIssues.firstOrNull { it.id !in knownIssueIds }
        knownIssueIds = knownIssueIds + issueIds
        if (newIssue != null) alertCargo(trip, DriverNotifier.ID_ISSUE, "적재 이상 의심", cargoIssueSpeech(newIssue.kind, atStop), atStop)

        if (camera == null || camera.id == lastCameraObservationId) return
        lastCameraObservationId = camera.id
        val differences = trip.comparison.differences
        if (trip.comparison.state != ComparisonState.MISMATCH || differences.isEmpty()) {
            lastCountDifferences = null
            return
        }
        // 정차 중에는 짐을 내리며 수량이 계속 바뀌고, 뒷문을 닫으면 하차 확인 팝업이 하차 후 기준으로 차이를 알린다.
        // 여기서 하차 전 기준으로 또 알리면 숫자가 서로 달라 혼란스러우므로 운전 중에만 알린다 (2026-11-15)
        if (atStop) {
            lastCountDifferences = null
            return
        }
        if (differences == lastCountDifferences) return
        lastCountDifferences = differences
        val first = differences.first()
        alertCargo(trip, DriverNotifier.ID_COUNT, "짐칸 수량 변화", cargoCountSpeech(first, trip.kindNames[first.kind] ?: first.kind, atStop), atStop)
    }

    private fun alertCargo(trip: Trip, notificationId: Int, title: String, text: String, atStop: Boolean) {
        speech?.speak(text)
        val stop = trip.currentStop ?: return
        if (!cargoPopupAllowed(appVisible.value)) return
        _arrival.value = ArrivalRequest(trip.id, stop.id, clock(), autoPlay = false)
        if (atStop) notify(notificationId, title, "눌러서 확인") else drivingNotice(notificationId, title)
    }

    /**
     * 운전 중 알림은 내비 회전 안내를 가리므로 [DRIVING_NOTICE_MS] 뒤 지운다. AAOS 헤드업은 스스로 사라지지 않는다(2026-11-15 실측).
     * 음성은 그대로 나간다.
     */
    private fun drivingNotice(id: Int, title: String) {
        notify(id, title, "눌러서 확인")
        noticeTimers.remove(id)?.cancel()
        noticeTimers[id] = scope.launch {
            delay(DRIVING_NOTICE_MS)
            cancelNotice(id)
        }
    }

    private companion object {
        const val REAR_DOOR_WARNING = "뒷문이 열려 있습니다. 안전한 곳에 정차해 확인해 주세요."
        const val REPEAT_WARNING_MS = 30_000L
        const val DRIVING_NOTICE_MS = 8_000L
        const val CARGO_POLL_MS = 10_000L
    }
}
