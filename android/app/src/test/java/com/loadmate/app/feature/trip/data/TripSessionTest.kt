package com.loadmate.app.feature.trip.data

import com.loadmate.app.core.pleos.NaviConnection
import com.loadmate.app.core.pleos.NaviDestination
import com.loadmate.app.core.pleos.NaviEvent
import com.loadmate.app.core.pleos.NaviGateway
import com.loadmate.app.core.pleos.NaviState
import com.loadmate.app.core.notify.DriverNotifier
import com.loadmate.app.core.notify.TripKeepAlive
import com.loadmate.app.core.pleos.RearDoorConnection
import com.loadmate.app.core.pleos.RearDoorGateway
import com.loadmate.app.core.pleos.RearDoorState
import com.loadmate.app.core.pleos.SpeechOutput
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.domain.TripRepository
import com.loadmate.app.feature.trip.domain.TripStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import com.loadmate.app.core.network.ApiResult
import com.loadmate.app.feature.trip.domain.Comparison
import com.loadmate.app.feature.trip.domain.ComparisonState
import com.loadmate.app.feature.trip.domain.Difference
import com.loadmate.app.feature.trip.domain.Issue
import com.loadmate.app.feature.trip.domain.ObservationInfo
import com.loadmate.app.feature.trip.domain.ObservationSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class TripSessionTest {
    private val trip by lazy {
        val body = javaClass.classLoader!!.getResourceAsStream("trip/trip_in_progress.json")!!.bufferedReader().readText()
        TripJsonParser.trip(body)
    }
    private class FakeNavi : NaviGateway {
        override val state = MutableStateFlow(NaviState(connection = NaviConnection.Connected))
        var connections = 0
        var releases = 0
        override fun connect() { connections++ }
        override fun requestRoute(destination: NaviDestination, waypoints: List<NaviDestination>) = Unit
        override fun cancelRoute() = Unit
        override fun release() { releases++ }
    }
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `화면 수명과 관계없이 운행 종료 때 내비 리스너 해제`() {
        val current = MutableStateFlow<Trip?>(null)
        val repo = Proxy.newProxyInstance(TripRepository::class.java.classLoader, arrayOf(TripRepository::class.java)) { _, method, _ ->
            if (method.name == "getCurrentTrip") current else error("unexpected ${method.name}")
        } as TripRepository
        val navi = FakeNavi()
        TripSession(repo, navi, null) { _, _, _ -> }
        current.value = trip
        assertEquals(1, navi.connections)
        assertEquals(0, navi.releases)
        current.value = trip.copy(status = TripStatus.COMPLETED)
        assertEquals(1, navi.releases)
    }

    private class FakeDoor : RearDoorGateway {
        override val state = MutableStateFlow(RearDoorState(connection = RearDoorConnection.Subscribed, position = 0, minPosition = 0))
        var holds = 0
        var releases = 0
        override fun start() = Unit
        override fun stop() = Unit
        override fun holdForTrip() { holds++ }
        override fun releaseTripHold() { releases++ }
        fun set(open: Boolean) { state.value = state.value.copy(position = if (open) 100 else 0) }
    }
    private class FakeKeepAlive : TripKeepAlive {
        var starts = 0
        var stops = 0
        override fun start() { starts++ }
        override fun stop() { stops++ }
    }
    private class FakeTts : SpeechOutput {
        val spoken = mutableListOf<String>()
        override val available: StateFlow<Boolean?> = MutableStateFlow(true)
        override val speaking: StateFlow<Boolean> = MutableStateFlow(false)
        override fun speak(text: String) { spoken += text }
        override fun release() = Unit
    }
    private inner class Fixture {
        val current = MutableStateFlow<Trip?>(trip)
        val navi = FakeNavi()
        val door = FakeDoor()
        val keepAlive = FakeKeepAlive()
        val tts = FakeTts()
        val visible = MutableStateFlow(false)
        val popups = mutableListOf<String>()
        val cancelled = mutableListOf<Int>()
        private val repo = Proxy.newProxyInstance(TripRepository::class.java.classLoader, arrayOf(TripRepository::class.java)) { _, method, _ ->
            when (method.name) {
                "getCurrentTrip" -> current
                "getTrip" -> { polls++; ApiResult.Success(current.value!!) }
                else -> error("unexpected ${method.name}")
            }
        } as TripRepository
        var polls = 0
        val session = TripSession(repo, navi, tts, door, keepAlive, visible, { 1_000L }, { cancelled += it }) { _, title, _ -> popups += title }
        /** runTest 는 가상 시간의 작업이 없어질 때까지 돈다. 운행을 끝내 주기 확인을 멈춘다 */
        fun finish() { current.value = trip.copy(status = TripStatus.COMPLETED) }
        fun guiding() { navi.state.value = navi.state.value.copy(events = listOf(NaviEvent.RouteStarted("A", 100, 60, 2), NaviEvent.RouteRequested("A", 1))) }
        fun arrive(at: Long) { navi.state.value = navi.state.value.copy(events = listOf<NaviEvent>(NaviEvent.WaypointArrived(at)) + navi.state.value.events) }
    }

    @Test fun `운전 중 뒷문이 열리면 음성과 위쪽 알림으로 경고하고 열려 있으면 한 번 더 말한다`() = runTest(UnconfinedTestDispatcher().also { Dispatchers.setMain(it) }) {
        val f = Fixture()
        f.guiding()
        f.cancelled.clear() // 시작할 때 닫힘 상태로 한 번 지운다
        f.door.set(true)
        assertEquals(1, f.tts.spoken.size)
        assertEquals(listOf("뒷문 열림"), f.popups)
        assertEquals(false, f.session.arrival.value?.autoPlay)
        advanceTimeBy(7_000)
        assertEquals(emptyList<Int>(), f.cancelled)
        advanceTimeBy(1_001)
        assertEquals(listOf(DriverNotifier.ID_REAR_DOOR), f.cancelled)
        assertEquals(1_000L, f.session.doorWarningAt.value)
        advanceTimeBy(30_001)
        assertEquals(2, f.tts.spoken.size)
        advanceTimeBy(120_000)
        assertEquals(2, f.tts.spoken.size)
        assertEquals(1, f.popups.size)
        f.finish()
    }

    @Test fun `닫으면 반복 경고를 멈춘다`() = runTest(UnconfinedTestDispatcher().also { Dispatchers.setMain(it) }) {
        val f = Fixture()
        f.guiding()
        f.door.set(true)
        f.door.set(false)
        advanceTimeBy(60_000)
        assertEquals(1, f.tts.spoken.size)
        assertTrue(DriverNotifier.ID_REAR_DOOR in f.cancelled)
        f.finish()
    }

    @Test fun `Loadmate 를 보고 있거나 도착 후 뒷문을 열면 경고하지 않는다`() {
        val visible = Fixture()
        visible.guiding()
        visible.visible.value = true
        visible.door.set(true)
        assertEquals(emptyList<String>(), visible.tts.spoken)

        val arrived = Fixture()
        arrived.guiding()
        arrived.arrive(5)
        arrived.door.set(true)
        assertEquals(emptyList<String>(), arrived.tts.spoken)
    }

    @Test fun `안내 중이 아니면 경고하지 않는다`() {
        val f = Fixture()
        f.door.set(true)
        assertEquals(emptyList<String>(), f.tts.spoken)
    }

    @Test fun `도착 신호에 말하지 않고 팝업만 한 번 띄운다`() {
        val f = Fixture()
        f.guiding()
        f.arrive(5)
        f.arrive(6)
        assertEquals(1, f.popups.size)
        assertEquals(emptyList<String>(), f.tts.spoken)
        assertEquals(trip.currentStop?.id, f.session.arrival.value?.stopId)
        f.session.consumeArrival()
        assertEquals(null, f.session.arrival.value)
    }

    @Test fun `팝업을 놓치면 기사가 다시 말해 띄울 수 있다`() {
        val f = Fixture()
        f.session.markArrived()
        f.session.markArrived()
        assertEquals(2, f.popups.size)
    }

    @Test fun `앱이 앞에 있으면 팝업 없이 도착 요청만 남긴다`() {
        val f = Fixture()
        f.visible.value = true
        f.session.markArrived()
        assertEquals(emptyList<String>(), f.popups)
        assertEquals(trip.id, f.session.arrival.value?.tripId)
    }

    @Test fun `도착한 방문지에서 뒷문을 열었다 닫으면 하차 판정을 요청한다`() {
        val f = Fixture()
        f.session.markArrived()
        f.door.set(true)
        assertEquals(null, f.session.unloadSignal.value.askStopId)
        f.door.set(false)
        assertEquals(trip.currentStop!!.id, f.session.unloadSignal.value.askStopId)
    }

    @Test fun `도착 신호를 놓쳐도 앱을 보며 뒷문을 여닫으면 도착으로 보고 하차 확인을 묻는다`() {
        val f = Fixture()
        f.visible.value = true
        f.door.set(true)
        f.door.set(false)
        assertEquals(trip.currentStop!!.id, f.session.unloadSignal.value.arrivedStopId)
        assertEquals(trip.currentStop!!.id, f.session.unloadSignal.value.askStopId)
    }

    @Test fun `도착 전이고 앱이 뒤에 있으면 뒷문을 여닫아도 묻지 않는다`() {
        val f = Fixture()
        f.door.set(true)
        f.door.set(false)
        assertEquals(null, f.session.unloadSignal.value.askStopId)
    }

    @Test fun `받아 간 하차 판정 요청은 지운다`() {
        val f = Fixture()
        f.visible.value = true
        f.session.markArrived()
        f.door.set(true)
        f.door.set(false)
        f.session.setUnloadAsk(null)
        assertEquals(null, f.session.unloadSignal.value.askStopId)
    }

    @Test fun `내비 실패는 음성으로만 알린다`() {
        val f = Fixture()
        f.navi.state.value = f.navi.state.value.copy(events = listOf(NaviEvent.Error("CAUSE_SEARCH_ROUTE", 2), NaviEvent.RouteRequested("A", 1)))
        assertEquals(1, f.tts.spoken.size)
        assertEquals(emptyList<String>(), f.popups)
    }

    private fun issue(id: Int, kind: String = "shift") = Issue(id, kind, "rear", "", ObservationSource.RECORDED_VIDEO, "2026-11-09T00:00:00Z", true, null)
    private fun camera(id: Int, observed: Int, source: ObservationSource = ObservationSource.RECORDED_VIDEO): Comparison =
        trip.comparison.copy(
            state = ComparisonState.MISMATCH,
            differences = listOf(Difference("box", 10, observed)),
            observation = ObservationInfo(id, source, true, mapOf("box" to observed), "2026-11-09T00:00:00Z"),
        )

    @Test fun `운전 중 새 카메라 이상은 음성과 위쪽 알림으로 알린다`() {
        val f = Fixture()
        f.current.value = trip.copy(issues = listOf(issue(1)))
        assertEquals(1, f.tts.spoken.size)
        assertTrue(f.tts.spoken[0].contains("정차한 뒤"))
        assertEquals(listOf("적재 이상 의심"), f.popups)
        assertEquals(false, f.session.arrival.value?.autoPlay)
    }

    @Test fun `운전 중 알림은 8초 뒤 지우고 정차 중 알림은 남긴다`() = runTest(UnconfinedTestDispatcher().also { Dispatchers.setMain(it) }) {
        val f = Fixture()
        f.cancelled.clear()
        f.current.value = trip.copy(issues = listOf(issue(1)))
        advanceTimeBy(8_001)
        assertEquals(listOf(DriverNotifier.ID_ISSUE), f.cancelled)
        f.session.markArrived()
        f.current.value = trip.copy(issues = listOf(issue(1), issue(2)))
        advanceTimeBy(60_000)
        assertEquals(listOf(DriverNotifier.ID_ISSUE), f.cancelled)
        f.finish()
    }

    @Test fun `도착 후 앱이 뒤에 있으면 음성과 팝업으로 알리고 재생 없이 적재 화면을 연다`() {
        val f = Fixture()
        f.session.markArrived()
        f.session.consumeArrival()
        f.popups.clear()
        f.current.value = trip.copy(issues = listOf(issue(1, "leak")))
        assertEquals(listOf("적재 이상 의심"), f.popups)
        assertTrue(f.tts.spoken.last().contains("누액"))
        assertEquals(false, f.session.arrival.value?.autoPlay)
    }

    @Test fun `도착 후라도 앱을 보고 있으면 팝업은 띄우지 않는다`() {
        val f = Fixture()
        f.session.markArrived()
        f.popups.clear()
        f.visible.value = true
        f.current.value = trip.copy(issues = listOf(issue(1)))
        assertEquals(1, f.tts.spoken.size)
        assertEquals(emptyList<String>(), f.popups)
    }

    @Test fun `처음 받은 운행의 기존 이상은 다시 말하지 않고 같은 이상도 한 번만 말한다`() {
        val f = Fixture()
        val fresh = MutableStateFlow<Trip?>(trip.copy(issues = listOf(issue(1))))
        assertEquals(emptyList<String>(), f.tts.spoken)
        assertEquals(1, fresh.value!!.issues.size)
        f.current.value = trip.copy(issues = listOf(issue(2)))
        f.current.value = trip.copy(issues = listOf(issue(2)), driverId = "changed")
        assertEquals(1, f.tts.spoken.size)
    }

    @Test fun `카메라 수량 차이만 알리고 기사 입력 관측은 알리지 않는다`() {
        val f = Fixture()
        f.current.value = trip.copy(comparison = camera(5, 8, ObservationSource.MANUAL))
        assertEquals(emptyList<String>(), f.tts.spoken)
        f.current.value = trip.copy(comparison = camera(6, 8))
        assertEquals(1, f.tts.spoken.size)
        assertTrue(f.tts.spoken[0].contains("예상 10개, 카메라 8개"))
        assertEquals(listOf("짐칸 수량 변화"), f.popups)
        f.current.value = trip.copy(comparison = camera(7, 8))
        assertEquals(1, f.tts.spoken.size)
        f.current.value = trip.copy(comparison = camera(8, 7))
        assertEquals(2, f.tts.spoken.size)
    }

    @Test fun `짐을 내리는 중에는 카메라 수량이 바뀌어도 알리지 않는다 (하차 확인 팝업이 맡는다)`() {
        val f = Fixture()
        f.session.markArrived()
        f.popups.clear()
        f.current.value = trip.copy(comparison = camera(6, 9))
        f.current.value = trip.copy(comparison = camera(7, 8))
        f.current.value = trip.copy(comparison = camera(8, 7))
        assertEquals(emptyList<String>(), f.tts.spoken)
        assertEquals(emptyList<String>(), f.popups)
    }

    @Test fun `운행 중에는 카메라 결과를 주기적으로 받아 오고 끝나면 멈춘다`() = runTest(UnconfinedTestDispatcher().also { Dispatchers.setMain(it) }) {
        val f = Fixture()
        advanceTimeBy(20_001)
        assertEquals(2, f.polls)
        f.current.value = trip.copy(status = TripStatus.COMPLETED)
        advanceTimeBy(60_000)
        assertEquals(2, f.polls)
    }

    @Test fun `운행 중에만 뒷문 구독과 앱 유지를 잡는다`() {
        val f = Fixture()
        assertEquals(1, f.door.holds)
        assertEquals(1, f.keepAlive.starts)
        f.current.value = trip.copy(status = TripStatus.COMPLETED)
        assertEquals(1, f.door.releases)
        assertEquals(1, f.keepAlive.stops)
    }
}
