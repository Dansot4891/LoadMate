package com.loadmate.app.feature.trip.presentation

import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.feature.trip.presentation.driving.DrivingUiAction
import com.loadmate.app.feature.trip.presentation.driving.DrivingViewModel
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
import com.loadmate.app.feature.trip.domain.ComparisonState
import com.loadmate.app.feature.trip.domain.Counts
import com.loadmate.app.feature.trip.domain.HistoryItem
import com.loadmate.app.feature.trip.domain.NewCargo
import com.loadmate.app.feature.trip.domain.ObservationSource
import com.loadmate.app.feature.trip.domain.PlacementEvent
import com.loadmate.app.feature.trip.domain.PlacementType
import com.loadmate.app.feature.trip.domain.RoutePoint
import com.loadmate.app.feature.trip.domain.RoutePoints
import com.loadmate.app.feature.trip.domain.CargoSlot
import com.loadmate.app.feature.trip.domain.CargoUnit
import com.loadmate.app.feature.trip.domain.CargoUnitState
import com.loadmate.app.feature.trip.domain.StopStatus
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.domain.TripRepository
import com.loadmate.app.feature.trip.domain.TripSummary
import com.loadmate.app.feature.trip.domain.UnloadSignal
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
class TripCoordinatorTest {

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
        val addedStops = mutableListOf<Triple<String, Double?, Double?>>() // address, lat, lng
        override suspend fun addStop(tripId: String, key: String, name: String, address: String, lat: Double?, lng: Double?, cargo: List<NewCargo>): ApiResult<Trip> {
            addedStops += Triple(address, lat, lng)
            return ApiResult.Success(trip)
        }
        var addressResult: ApiResult<Boolean> = ApiResult.Success(true)
        override suspend fun checkAddress(address: String) = addressResult
        val placeChanges = mutableListOf<Triple<String, String, String>>() // stopId, name, address
        override suspend fun changeStopPlace(tripId: String, stopId: String, key: String, name: String, address: String): ApiResult<Trip> {
            placeChanges += Triple(stopId, name, address)
            return ApiResult.Success(trip)
        }
        val reorders = mutableListOf<List<String>>()
        val routePointCalls = mutableListOf<List<String>>()
        var routePointsResult: ApiResult<RoutePoints> = ApiResult.Success(RoutePoints(emptyMap(), emptyList()))
        override suspend fun routePoints(tripId: String, stopIds: List<String>): ApiResult<RoutePoints> {
            routePointCalls += stopIds
            return routePointsResult
        }
        override suspend fun reorderStops(tripId: String, key: String, stopIds: List<String>): ApiResult<Trip> {
            reorders += stopIds
            return ApiResult.Success(trip)
        }
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
        val placements = mutableListOf<Pair<ObservationSource, List<PlacementEvent>>>()
        override suspend fun recordPlacements(tripId: String, key: String, source: ObservationSource, observedAtIso: String, events: List<PlacementEvent>): ApiResult<Trip> {
            placements += source to events
            return ApiResult.Success(trip)
        }
        val baselineCalls = mutableListOf<Pair<Counts, Int?>>()
        override suspend fun confirmBaseline(tripId: String, idempotencyKey: String, counts: Counts, observationId: Int?): ApiResult<Trip> {
            baselineCalls += counts to observationId
            return ApiResult.Success(tripInProgressAfterBaseline ?: trip)
        }
        var tripInProgressAfterBaseline: Trip? = null
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

    private fun vm(repo: FakeRepo, navi: FakeNavi = FakeNavi(), cached: Pair<Trip, Instant>? = null, signal: MutableStateFlow<UnloadSignal> = MutableStateFlow(UnloadSignal())) =
        TripCoordinator(
            repo, memos = null, navi = navi, rearDoor = FakeDoor(), speech = null, notify = { _, _, _ -> }, cached = { cached },
            tripId = "sample-trip-1", scope = CoroutineScope(UnconfinedTestDispatcher()), now = { Instant.parse("2026-11-08T07:00:00Z") }, newKey = { "key-${++keyN}" }, unloadSignal = signal, setUnloadAsk = { signal.value = signal.value.copy(askStopId = it) },
        )

    private fun completed(): ApiResult<CompleteResult> {
        val stop = needsConfirmation.stop.copy(status = StopStatus.COMPLETED, resolution = "matched")
        return ApiResult.Success(needsConfirmation.copy(completed = true, stop = stop, expectedRemaining = mapOf("box" to 7)))
    }

    @Test
    fun `운행 화면은 현재 단계만 표시하고 내비 수명은 관리하지 않음`() {
        val navi = FakeNavi()
        val v = vm(FakeRepo(tripInProgress), navi)
        // state 는 WhileSubscribed → 수집 중일 때 값이 갱신된다
        val job = CoroutineScope(UnconfinedTestDispatcher()).launch { v.state.collect {} }
        assertEquals(TripStep.DRIVING, v.state.value.step)
        job.cancel()
        assertEquals(0, navi.connects)
        assertEquals(0, navi.releases)
    }

    private fun doorClosed(signal: MutableStateFlow<UnloadSignal>) { signal.value = signal.value.copy(askStopId = tripInProgress.currentStop!!.id) }

    @Test
    fun `뒷문이 닫혀 관측과 맞으면 묻지 않고 계획 수량으로 완료한다`() {
        val repo = FakeRepo(tripInProgress)
        repo.completeResults.add(completed())
        val signal = MutableStateFlow(UnloadSignal())
        val v = vm(repo, signal = signal)
        doorClosed(signal)
        assertEquals(1, repo.completeCalls.size)
        assertEquals(false, repo.completeCalls[0].third)
        assertEquals(mapOf("box" to 3), repo.completeCalls[0].second)
        assertEquals(null, v.local.value.unloadAskStopId)
        assertEquals(null, signal.value.askStopId)
    }

    @Test
    fun `수량이 다르면 차이를 보여 주고 넘어가면 같은 요청 ID로 기사 확인 완료`() {
        val repo = FakeRepo(tripInProgress)
        repo.completeResults.addAll(listOf(ApiResult.Success(needsConfirmation), completed()))
        val signal = MutableStateFlow(UnloadSignal())
        val v = vm(repo, signal = signal)
        doorClosed(signal)
        assertEquals(needsConfirmation.comparison, v.local.value.pendingConfirmation)
        assertEquals(1, repo.completeCalls.size)
        v.onAction(TripUiAction.PassUnloadMismatch)
        assertEquals(repo.completeCalls[0].first, repo.completeCalls[1].first)
        assertEquals(true, repo.completeCalls[1].third)
        assertEquals(null, v.local.value.pendingConfirmation)
    }

    @Test
    fun `비교할 관측이 없으면 팝업으로 묻고 맞아요면 같은 요청 ID로 완료한다`() {
        val repo = FakeRepo(tripInProgress)
        val noObservation = needsConfirmation.copy(comparison = needsConfirmation.comparison.copy(state = ComparisonState.NO_OBSERVATION))
        repo.completeResults.addAll(listOf(ApiResult.Success(noObservation), completed()))
        val signal = MutableStateFlow(UnloadSignal())
        val v = vm(repo, signal = signal)
        doorClosed(signal)
        assertEquals(1, repo.completeCalls.size)
        assertEquals(tripInProgress.currentStop!!.id, v.local.value.unloadAskStopId)
        v.onAction(TripUiAction.ConfirmUnload)
        assertEquals(listOf(false, true), repo.completeCalls.map { it.third })
        assertEquals(repo.completeCalls[0].first, repo.completeCalls[1].first)
        assertEquals(null, v.local.value.unloadAskStopId)
    }

    @Test
    fun `서버에 닿지 못하면 팝업을 열어 같은 요청 ID로 재시도하고 완료 후 새 ID`() {
        val repo = FakeRepo(tripInProgress)
        repo.completeResults.addAll(listOf(ApiResult.Failure(ApiError.Network("SocketTimeoutException")), completed(), ApiResult.Success(needsConfirmation)))
        val signal = MutableStateFlow(UnloadSignal())
        val v = vm(repo, signal = signal)
        doorClosed(signal)
        assertEquals(tripInProgress.currentStop!!.id, v.local.value.unloadAskStopId)
        v.onAction(TripUiAction.ConfirmUnload)
        assertEquals(repo.completeCalls[0].first, repo.completeCalls[1].first)
        v.onAction(TripUiAction.ConfirmUnload)
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

    private val beforeDeparture: Trip by lazy {
        tripInProgress.copy(baseline = null, status = com.loadmate.app.feature.trip.domain.TripStatus.PLANNED, comparison = tripInProgress.comparison.copy(observation = null))
    }

    @Test
    fun `출발 확인은 관측이 없으면 계획 수량으로 기준을 확정하고 내비 확인을 연다`() {
        val repo = FakeRepo(beforeDeparture).apply { tripInProgressAfterBaseline = tripInProgress }
        val v = vm(repo)
        assertEquals(TripStep.OVERVIEW, v.local.value.step)
        v.onAction(TripUiAction.AskDeparture)
        assertTrue(v.local.value.departureDialog)
        assertTrue(repo.baselineCalls.isEmpty())
        v.onAction(TripUiAction.ConfirmDeparture)
        assertEquals(listOf(beforeDeparture.plannedTotals to null), repo.baselineCalls)
        assertEquals(false, v.local.value.departureDialog)
        assertEquals(TripStep.DRIVING, v.local.value.step)
        assertTrue(v.local.value.routeDialog != null)
    }

    @Test
    fun `출발 확인을 닫으면 기준을 확정하지 않는다`() {
        val repo = FakeRepo(beforeDeparture)
        val v = vm(repo)
        v.onAction(TripUiAction.AskDeparture)
        v.onAction(TripUiAction.DismissDeparture)
        assertEquals(false, v.local.value.departureDialog)
        assertTrue(repo.baselineCalls.isEmpty())
    }

    @Test
    fun `운행 화면은 종료 시 내비를 직접 해제하지 않음`() {
        val navi = FakeNavi()
        vm(FakeRepo(tripInProgress.copy(status = com.loadmate.app.feature.trip.domain.TripStatus.COMPLETED)), navi)
        assertEquals(0, navi.releases)
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
    fun `화면 운행을 놓으면 Coordinator 작업만 취소하고 내비 리스너는 유지`() {
        val navi = FakeNavi()
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        val coordinator = TripCoordinator(
            FakeRepo(tripInProgress), null, navi, FakeDoor(), null, { _, _, _ -> }, { null },
            "sample-trip-1", scope,
        )
        coordinator.release()
        assertEquals(true, scope.coroutineContext[kotlinx.coroutines.Job]?.isCancelled)
        assertEquals(0, navi.releases)
    }

    @Test
    fun `서버 연결 실패 시 이번 실행에서 받은 운행을 오프라인 표시와 함께 유지`() {
        val repo = object : TripRepository by FakeRepo(tripInProgress) {
            override suspend fun getTrip(tripId: String): ApiResult<Trip> = ApiResult.Failure(ApiError.Network("ConnectException"))
        }
        val at = Instant.parse("2026-11-08T06:59:00Z")
        val v = TripCoordinator(repo, null, FakeNavi(), FakeDoor(), null, { _, _, _ -> }, { tripInProgress to at }, "sample-trip-1", CoroutineScope(UnconfinedTestDispatcher()))
        val job = CoroutineScope(UnconfinedTestDispatcher()).launch { v.state.collect {} }
        assertEquals(at, v.state.value.offlineSince)
        assertEquals("sample-trip-1", v.state.value.trip?.id)
        job.cancel()
    }

    private fun coordinatorFor(repo: TripRepository): Pair<TripCoordinator, kotlinx.coroutines.Job> {
        val v = TripCoordinator(repo, null, FakeNavi(), FakeDoor(), null, { _, _, _ -> }, { null }, "sample-trip-1", CoroutineScope(UnconfinedTestDispatcher()))
        return v to CoroutineScope(UnconfinedTestDispatcher()).launch { v.state.collect {} }
    }

    private val tripWithoutCoordinates: Trip by lazy {
        tripInProgress.copy(stops = tripInProgress.stops.map { it.copy(latitude = null, longitude = null) })
    }

    @Test
    fun `저장된 좌표가 있으면 서버에 묻지 않고 전달 확인을 연다`() {
        val repo = FakeRepo(tripInProgress)
        val (v, job) = coordinatorFor(repo)
        v.onAction(TripUiAction.AskRoute)
        assertEquals(emptyList<List<String>>(), repo.routePointCalls)
        assertEquals(listOf("A", "B", "C"), v.state.value.routeDialog?.stopLabels)
        job.cancel()
    }

    @Test
    fun `좌표가 없는 방문지는 서버에서 받아 이번 전달에 쓴다`() {
        val repo = FakeRepo(tripWithoutCoordinates)
        val ids = tripWithoutCoordinates.pendingStops.map { it.id }
        repo.routePointsResult = ApiResult.Success(RoutePoints(ids.associateWith { RoutePoint(37.5, 127.2) }, emptyList()))
        val (v, job) = coordinatorFor(repo)
        v.onAction(TripUiAction.AskRoute)
        assertEquals(listOf(ids), repo.routePointCalls)
        assertEquals(37.5, v.state.value.routeDialog?.destination?.latitude ?: 0.0, 0.0)
        assertEquals(false, v.state.value.busy)
        job.cancel()
    }

    @Test
    fun `위치를 찾지 못한 방문지가 있으면 내비로 보내지 않고 알린다`() {
        val repo = FakeRepo(tripWithoutCoordinates)
        val stops = tripWithoutCoordinates.pendingStops
        repo.routePointsResult = ApiResult.Success(RoutePoints(stops.drop(1).associate { it.id to RoutePoint(37.5, 127.2) }, listOf(stops.first().id)))
        val (v, job) = coordinatorFor(repo)
        v.onAction(TripUiAction.AskRoute)
        assertEquals(null, v.state.value.routeDialog)
        assertEquals(true, v.state.value.message?.isError)
        assertEquals(true, v.state.value.message?.text?.startsWith("${stops.first().label} 주소를 찾지 못했습니다"))
        job.cancel()
    }

    @Test
    fun `직접 입력한 방문지는 좌표 없이 입력값과 수량으로 추가한다`() {
        val repo = FakeRepo(tripInProgress)
        val (v, job) = coordinatorFor(repo)
        v.onAction(TripUiAction.AddStop("판교 납품처", "판교역로 235", 2))
        assertEquals(listOf(Triple("판교역로 235", null, null)), repo.addedStops)
        job.cancel()
    }

    @Test
    fun `위치 확인 결과는 확인한 주소와 함께 기억하고 닫으면 지운다`() {
        val repo = FakeRepo(tripInProgress)
        val (v, job) = coordinatorFor(repo)
        v.onAction(TripUiAction.CheckAddress("판교역로 235"))
        assertEquals("판교역로 235", v.state.value.addressCheck.foundAddress)
        repo.addressResult = ApiResult.Success(false)
        v.onAction(TripUiAction.CheckAddress("없는 주소"))
        assertEquals("없는 주소", v.state.value.addressCheck.notFoundAddress)
        assertEquals(null, v.state.value.addressCheck.foundAddress)
        v.onAction(TripUiAction.ClearAddressCheck)
        assertEquals(AddressCheckState(), v.state.value.addressCheck)
        job.cancel()
    }

    @Test
    fun `위치 확인 실패는 이유를 보여 준다`() {
        val repo = FakeRepo(tripInProgress)
        repo.addressResult = ApiResult.Failure(ApiError.Http(503, "place_search_not_configured", "장소 검색 키가 설정되지 않았습니다"))
        val (v, job) = coordinatorFor(repo)
        v.onAction(TripUiAction.CheckAddress("판교역로 235"))
        assertEquals("장소 검색 키가 설정되지 않았습니다", v.state.value.addressCheck.error)
        job.cancel()
    }

    @Test
    fun `장소 수정과 끌어서 바꾼 순서를 서버로 보낸다`() {
        val repo = FakeRepo(tripInProgress)
        val (v, job) = coordinatorFor(repo)
        v.onAction(TripUiAction.EditStopPlace("stop-b", "새 이름", "새 주소"))
        assertEquals(listOf(Triple("stop-b", "새 이름", "새 주소")), repo.placeChanges)
        v.onAction(TripUiAction.ReorderStops(listOf("stop-c", "stop-b")))
        assertEquals(listOf(listOf("stop-c", "stop-b")), repo.reorders)
        job.cancel()
    }

}
