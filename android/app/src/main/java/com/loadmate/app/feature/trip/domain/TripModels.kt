package com.loadmate.app.feature.trip.domain

import com.loadmate.app.core.network.ApiResult
import kotlinx.coroutines.flow.StateFlow

/** 종류별 개수 (예: box → 10). 서로 다른 종류는 합산하지 않는다 (기획서 16장). */
typealias Counts = Map<String, Int>

/** 서버 계약: docs/api-contract.md "운행". 잔량·완료 판정의 최종 기준은 서버 (원칙 2). */
data class TripSummary(val id: String, val name: String, val status: TripStatus, val dataSource: String, val stopCount: Int)

data class Trip(
    val id: String,
    val name: String,
    /** "sample" 이면 화면에 샘플 데이터로 표시 (원칙 5) */
    val dataSource: String,
    val status: TripStatus,
    val baseline: Baseline?,
    val stops: List<Stop>,
    /** 기준 확정 전에는 null */
    val expectedRemaining: Counts?,
    val comparison: Comparison,
    val driverId: String? = null,
    val vehicleId: String? = null,
    val endReason: String? = null,
    val pickups: List<Pickup> = emptyList(),
    val issues: List<Issue> = emptyList(),
    /** 박스별 자리 기록 (F12·F13). 표시용이며 잔량·완료 판정에 쓰지 않는다 */
    val units: List<CargoUnit> = emptyList(),
) {
    val loadedPickups: List<Pickup> get() = pickups.filter { it.loaded }
    val openIssues: List<Issue> get() = issues.filter { it.open }
    val isEnded: Boolean get() = status == TripStatus.COMPLETED || status == TripStatus.ENDED_INCOMPLETE

    val currentStop: Stop? get() = stops.firstOrNull { it.status == StopStatus.PENDING }
    val pendingStops: List<Stop> get() = stops.filter { it.status == StopStatus.PENDING }

    /** 계획 수량 합계 (검수 목록) */
    val plannedTotals: Counts
        get() = stops.flatMap { it.cargo }.groupingBy { it.kind }.fold(0) { acc, l -> acc + l.plannedQty }

    /** kind → 화면 이름 (예: box → 박스) */
    val kindNames: Map<String, String>
        // 같은 종류면 배송품 이름을 우선한다 (회수품 "반품 박스" 가 배송품 "박스" 를 덮지 않게)
        get() = pickups.associate { it.kind to it.itemName } + stops.flatMap { it.cargo }.associate { it.kind to it.itemName }
}

enum class TripStatus { PLANNED, IN_PROGRESS, COMPLETED, ENDED_INCOMPLETE, UNKNOWN }

data class Baseline(val counts: Counts, val confirmedAt: String, val observationId: Int?)

data class Stop(
    val id: String,
    val seq: Int,
    val label: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val status: StopStatus,
    /** matched = 관측과 일치해 완료, driver_confirmed = 기사 확인으로 완료 */
    val resolution: String?,
    val cargo: List<CargoLine>,
    val placeKey: String = "",
    val cancelReason: String? = null,
) {
    val plannedCounts: Counts get() = cargo.groupingBy { it.kind }.fold(0) { acc, l -> acc + l.plannedQty }
}

enum class StopStatus { PENDING, COMPLETED, PARTIAL, CANCELLED, UNKNOWN }

/** zone: 적재 구역 rear(뒷문 쪽)·middle·front. 불확실하면 null (F13) */
data class CargoLine(val id: Int, val itemName: String, val kind: String, val plannedQty: Int, val deliveredQty: Int, val zone: String? = null)

/** 적재함 격자의 한 칸. zone front·middle·rear, lane 0(왼쪽)~2(오른쪽, 운전석을 바라본 기준), layer 1~3층 */
data class CargoSlot(val zone: String, val lane: Int, val layer: Int)

enum class CargoUnitState { PLANNED, LOADED, UNLOADED, UNKNOWN }

/** 박스 한 개. slot 은 기록된 자리, plannedSlot 은 계획 자리. 칸이 모자라면 둘 다 null (자리 미정) */
data class CargoUnit(
    val id: Int,
    val stopId: String?,
    val pickupId: Int?,
    val kind: String,
    val itemName: String,
    val no: Int,
    val state: CargoUnitState,
    val loadSeq: Int,
    val slot: CargoSlot?,
    val plannedSlot: CargoSlot?,
    val positionSource: ObservationSource?,
) {
    val misplaced: Boolean get() = state == CargoUnitState.LOADED && slot != null && plannedSlot != null && slot != plannedSlot
}

/** 자리 소식. 카메라·샘플 시뮬레이션·기사 입력이 같은 형식으로 보낸다 */
data class PlacementEvent(val unitId: Int, val type: PlacementType, val slot: CargoSlot? = null)

enum class PlacementType(val wire: String) { PLACED("placed"), MOVED("moved"), REMOVED("removed"), ACCEPTED("accepted") }

/** 회수품·추가 상차 (F44~F46) */
data class Pickup(val id: Int, val stopId: String?, val itemName: String, val kind: String, val qty: Int, val returnPlace: String, val loaded: Boolean)

/** 적재 이상 의심 (F30·F31·F34). 의심일 뿐 파손·책임을 확정하지 않는다 */
data class Issue(val id: Int, val kind: String, val zone: String?, val note: String, val source: ObservationSource, val observedAt: String, val open: Boolean, val resolution: String?, val unitId: Int? = null)

/** 수정 이력 (F64) */
data class HistoryItem(val type: String, val source: String, val actorId: String?, val createdAt: String, val summary: String)

data class NewCargo(val itemName: String, val kind: String, val qty: Int)

data class Comparison(
    val state: ComparisonState,
    val expected: Counts?,
    val observed: Counts?,
    val differences: List<Difference>,
    val observation: ObservationInfo?,
    /** 항상 true: 개별 물품 확인이 아닌 총량 검사 (R06) */
    val countOnly: Boolean,
    val staleAfterSeconds: Int,
)

enum class ComparisonState { MATCH, MISMATCH, NO_OBSERVATION, UNAVAILABLE, STALE, UNKNOWN }

data class Difference(val kind: String, val expected: Int, val observed: Int)

/** 정차 중 하차 확인에 쓰는 신호. 도착 처리된 방문지와, 지금 하차 여부를 물어볼 방문지. 화면 전용이며 서버에 저장하지 않는다 */
data class UnloadSignal(val arrivedStopId: String? = null, val askStopId: String? = null)

data class ObservationInfo(
    val id: Int,
    val source: ObservationSource,
    val available: Boolean,
    val counts: Counts?,
    val observedAt: String,
    /** 구역별 수량 (F12). 예: rear → {box: 3} */
    val zones: Map<String, Counts>? = null,
)

/** 관측 출처. 화면에 그대로 표시한다 (원칙 5). 카메라 API가 없어 지금은 SAMPLE·MANUAL 만 앱에서 보낸다. */
enum class ObservationSource(val wire: String, val label: String) {
    SAMPLE("sample", "샘플 관측"),
    MANUAL("manual", "기사 입력"),
    RECORDED_VIDEO("recorded_video", "녹화 영상 분석"),
    UNKNOWN("unknown", "출처 알 수 없음"),
}

data class CompleteResult(
    /** false 면 needs_confirmation — 서버에 아무것도 저장되지 않음 */
    val completed: Boolean,
    val replayed: Boolean,
    val stop: Stop,
    val expectedRemaining: Counts,
    val comparison: Comparison,
)

interface TripRepository {
    val currentTrip: StateFlow<Trip?>
    suspend fun listTrips(): ApiResult<List<TripSummary>>
    suspend fun getTrip(tripId: String): ApiResult<Trip>
    suspend fun recordObservation(
        tripId: String,
        idempotencyKey: String,
        source: ObservationSource,
        /** null 이면 확인 불가(카메라 가림 등) */
        counts: Counts?,
        observedAtIso: String,
        zones: Map<String, Counts>? = null,
    ): ApiResult<Comparison>
    suspend fun confirmBaseline(tripId: String, idempotencyKey: String, counts: Counts, observationId: Int?): ApiResult<Trip>
    suspend fun completeStop(
        tripId: String,
        stopId: String,
        idempotencyKey: String,
        delivered: Counts,
        driverConfirmed: Boolean,
    ): ApiResult<CompleteResult>

    // 운행 관리·회수·이상 (응답은 갱신된 운행). source: "screen" | "voice"
    suspend fun addStop(tripId: String, key: String, name: String, address: String, lat: Double, lng: Double, cargo: List<NewCargo>): ApiResult<Trip>
    suspend fun reorderStops(tripId: String, key: String, stopIds: List<String>): ApiResult<Trip>
    suspend fun cancelStop(tripId: String, stopId: String, key: String, reason: String): ApiResult<Trip>
    suspend fun changeCargoQty(tripId: String, stopId: String, key: String, cargoLineId: Int, plannedQty: Int): ApiResult<Trip>
    suspend fun endTrip(tripId: String, key: String, reason: String?): ApiResult<Trip>
    suspend fun addPickup(tripId: String, key: String, stopId: String?, itemName: String, kind: String, qty: Int, returnPlace: String, source: String): ApiResult<Trip>
    suspend fun returnPickup(tripId: String, pickupId: Int, key: String): ApiResult<Trip>
    suspend fun recordIssue(tripId: String, key: String, kind: String, zone: String?, note: String, source: ObservationSource, observedAtIso: String): ApiResult<Trip>
    suspend fun resolveIssue(tripId: String, issueId: Int, key: String, resolution: String, note: String?): ApiResult<Trip>
    suspend fun history(tripId: String): ApiResult<List<HistoryItem>>
    suspend fun recordPlacements(tripId: String, key: String, source: ObservationSource, observedAtIso: String, events: List<PlacementEvent>): ApiResult<Trip>
}
