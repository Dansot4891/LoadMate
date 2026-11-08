package com.loadmate.app.feature.trip.presentation

import com.loadmate.app.core.pleos.NaviConnection
import com.loadmate.app.core.pleos.NaviDestination
import com.loadmate.app.core.pleos.NaviGateway
import com.loadmate.app.core.pleos.NaviState
import com.loadmate.app.core.pleos.RearDoorGateway
import com.loadmate.app.core.pleos.RearDoorState
import com.loadmate.app.feature.trip.data.TripJsonParser
import com.loadmate.app.core.network.ApiError
import com.loadmate.app.core.network.ApiResult
import com.loadmate.app.feature.trip.domain.Comparison
import com.loadmate.app.feature.trip.domain.CompleteResult
import com.loadmate.app.feature.trip.domain.Counts
import com.loadmate.app.feature.trip.domain.HistoryItem
import com.loadmate.app.feature.trip.domain.NewCargo
import com.loadmate.app.feature.trip.domain.ObservationSource
import com.loadmate.app.feature.trip.domain.StopStatus
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.domain.TripRepository
import com.loadmate.app.feature.trip.domain.TripSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TripViewModelTest {

    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream("trip/$name")!!.bufferedReader().readText()
    private val tripInProgress: Trip by lazy { TripJsonParser.trip(res("trip_in_progress.json")) }
    private val needsConfirmation: CompleteResult by lazy { TripJsonParser.completeResult(res("complete_needs_confirmation.json")) }

    private class FakeRepo(var trip: Trip) : TripRepository {
        override val currentTrip = kotlinx.coroutines.flow.MutableStateFlow<Trip?>(trip)
        val completeCalls = mutableListOf<Triple<String, Counts, Boolean>>() // key, delivered, driverConfirmed
        var completeResults = ArrayDeque<ApiResult<CompleteResult>>()
        override suspend fun listTrips(): ApiResult<List<TripSummary>> = ApiResult.Success(emptyList())
        override suspend fun getTrip(tripId: String): ApiResult<Trip> = ApiResult.Success(trip)
        override suspend fun recordObservation(tripId: String, idempotencyKey: String, source: ObservationSource, counts: Counts?, observedAtIso: String, zones: Map<String, Counts>?): ApiResult<Comparison> =
            ApiResult.Success(trip.comparison)
        val pickupKeys = mutableListOf<String>()
        override suspend fun addStop(tripId: String, key: String, name: String, address: String, lat: Double, lng: Double, cargo: List<NewCargo>) = ApiResult.Success(trip)
        override suspend fun reorderStops(tripId: String, key: String, stopIds: List<String>) = ApiResult.Success(trip)
        override suspend fun cancelStop(tripId: String, stopId: String, key: String, reason: String) = ApiResult.Success(trip)
        override suspend fun changeCargoQty(tripId: String, stopId: String, key: String, cargoLineId: Int, plannedQty: Int) = ApiResult.Success(trip)
        override suspend fun endTrip(tripId: String, key: String, reason: String?) = ApiResult.Success(trip)
        var pickupResult: ApiResult<Trip>? = null
        override suspend fun addPickup(tripId: String, key: String, stopId: String?, itemName: String, kind: String, qty: Int, returnPlace: String, source: String): ApiResult<Trip> {
            pickupKeys += key
            return pickupResult ?: ApiResult.Success(trip)
        }
        override suspend fun returnPickup(tripId: String, pickupId: Int, key: String) = ApiResult.Success(trip)
        override suspend fun recordIssue(tripId: String, key: String, kind: String, zone: String?, note: String, source: ObservationSource, observedAtIso: String) = ApiResult.Success(trip)
        override suspend fun resolveIssue(tripId: String, issueId: Int, key: String, resolution: String, note: String?) = ApiResult.Success(trip)
        override suspend fun history(tripId: String): ApiResult<List<HistoryItem>> = ApiResult.Success(emptyList())
        override suspend fun confirmBaseline(tripId: String, idempotencyKey: String, counts: Counts, observationId: Int?): ApiResult<Trip> = ApiResult.Success(trip)
        override suspend fun completeStop(tripId: String, stopId: String, idempotencyKey: String, delivered: Counts, driverConfirmed: Boolean): ApiResult<CompleteResult> {
            completeCalls += Triple(idempotencyKey, delivered, driverConfirmed)
            return completeResults.removeFirst()
        }
    }

    private class FakeNavi : NaviGateway {
        override val state: StateFlow<NaviState> = MutableStateFlow(NaviState(connection = NaviConnection.Connected))
        var connects = 0; var releases = 0
        val routes = mutableListOf<Pair<NaviDestination, List<NaviDestination>>>()
        override fun connect() { connects++ }
        override fun requestRoute(destination: NaviDestination, waypoints: List<NaviDestination>) { routes += destination to waypoints }
        override fun cancelRoute() = Unit
        override fun release() { releases++ }
    }

    private class FakeDoor : RearDoorGateway {
        override val state: StateFlow<RearDoorState> = MutableStateFlow(RearDoorState())
        override fun start() = Unit
        override fun stop() = Unit
    }

    private var keyN = 0

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm(repo: FakeRepo, navi: FakeNavi = FakeNavi(), cached: Pair<Trip, Instant>? = null) =
        TripViewModel(
            repo, memos = null, navi = navi, rearDoor = FakeDoor(), speech = null, notify = { _, _, _ -> }, cached = { cached },
            tripId = "sample-trip-1", now = { Instant.parse("2026-11-08T07:00:00Z") }, newKey = { "key-${++keyN}" },
        )

    private fun completed(): ApiResult<CompleteResult> {
        val stop = needsConfirmation.stop.copy(status = StopStatus.COMPLETED, resolution = "matched")
        return ApiResult.Success(needsConfirmation.copy(completed = true, stop = stop, expectedRemaining = mapOf("box" to 7)))
    }

    @Test
    fun `운행 중이면 내비 연결 유지, 화면 단계는 운행`() {
        val navi = FakeNavi()
        val v = vm(FakeRepo(tripInProgress), navi)
        // state 는 WhileSubscribed → 수집 중일 때 값이 갱신된다
        val job = CoroutineScope(UnconfinedTestDispatcher()).launch { v.state.collect {} }
        assertEquals(TripStep.DRIVING, v.state.value.step)
        job.cancel()
        assertTrue(navi.connects >= 1)
        assertEquals(0, navi.releases)
    }

    @Test
    fun `확인 필요 후 재요청과 기사 확인은 같은 요청 ID를 쓴다`() {
        val repo = FakeRepo(tripInProgress)
        repo.completeResults.addAll(listOf(ApiResult.Success(needsConfirmation), completed()))
        val v = vm(repo)
        v.onAction(TripUiAction.Arrived)
        v.onAction(TripUiAction.RequestComplete)
        v.onAction(TripUiAction.DriverConfirmComplete)
        assertEquals(2, repo.completeCalls.size)
        assertEquals(repo.completeCalls[0].first, repo.completeCalls[1].first)
        assertEquals(false, repo.completeCalls[0].third)
        assertEquals(true, repo.completeCalls[1].third)
        assertEquals(mapOf("box" to 3), repo.completeCalls[0].second) // 기본 하차 수량 = 계획
    }

    @Test
    fun `네트워크 실패 후 재시도도 같은 요청 ID, 완료 후 다음 요청은 새 ID`() {
        val repo = FakeRepo(tripInProgress)
        repo.completeResults.addAll(listOf(ApiResult.Failure(ApiError.Network("SocketTimeoutException")), completed(), ApiResult.Success(needsConfirmation)))
        val v = vm(repo)
        v.onAction(TripUiAction.Arrived)
        v.onAction(TripUiAction.RequestComplete)
        v.onAction(TripUiAction.RequestComplete)
        assertEquals(repo.completeCalls[0].first, repo.completeCalls[1].first)
        v.onAction(TripUiAction.Arrived)
        v.onAction(TripUiAction.RequestComplete)
        assertNotEquals(repo.completeCalls[1].first, repo.completeCalls[2].first)
    }

    @Test
    fun `내비 전달은 확인 후에만, 남은 방문지 A B C 를 경유지 2 + 목적지로`() {
        val navi = FakeNavi()
        val v = vm(FakeRepo(tripInProgress), navi)
        v.onAction(TripUiAction.AskRoute)
        assertTrue(navi.routes.isEmpty())
        v.onAction(TripUiAction.ConfirmRoute)
        assertEquals(1, navi.routes.size)
        assertEquals(2, navi.routes[0].second.size)
        assertTrue(navi.routes[0].first.name.startsWith("C"))
    }

    @Test
    fun `운행 완료면 내비 해제`() {
        val navi = FakeNavi()
        vm(FakeRepo(tripInProgress.copy(status = com.loadmate.app.feature.trip.domain.TripStatus.COMPLETED)), navi)
        assertTrue(navi.releases >= 1)
    }

    @Test
    fun `회수 기록 실패 후 같은 내용 재시도는 같은 요청 ID`() {
        val repo = FakeRepo(tripInProgress)
        repo.pickupResult = ApiResult.Failure(ApiError.Network("timeout"))
        val v = vm(repo)
        v.onAction(TripUiAction.OpenPickup(PickupDraft("빈 용기", "container", 2, "창고")))
        v.onAction(TripUiAction.SubmitPickup)
        repo.pickupResult = null
        v.onAction(TripUiAction.OpenPickup(PickupDraft("빈 용기", "container", 2, "창고")))
        v.onAction(TripUiAction.SubmitPickup)
        assertEquals(2, repo.pickupKeys.size)
        assertEquals(repo.pickupKeys[0], repo.pickupKeys[1])
    }

    @Test
    fun `서버 연결 실패 시 이번 실행에서 받은 운행을 오프라인 표시와 함께 유지`() {
        val repo = object : TripRepository by FakeRepo(tripInProgress) {
            override suspend fun getTrip(tripId: String): ApiResult<Trip> = ApiResult.Failure(ApiError.Network("ConnectException"))
        }
        val at = Instant.parse("2026-11-08T06:59:00Z")
        val v = TripViewModel(repo, null, FakeNavi(), FakeDoor(), null, { _, _, _ -> }, { tripInProgress to at }, "sample-trip-1")
        val job = CoroutineScope(UnconfinedTestDispatcher()).launch { v.state.collect {} }
        assertEquals(at, v.state.value.offlineSince)
        assertEquals("sample-trip-1", v.state.value.trip?.id)
        job.cancel()
    }
}
