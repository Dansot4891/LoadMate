package com.loadmate.app.feature.trip.domain

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
) {
    val currentStop: Stop? get() = stops.firstOrNull { it.status == StopStatus.PENDING }
    val pendingStops: List<Stop> get() = stops.filter { it.status == StopStatus.PENDING }

    /** 계획 수량 합계 (검수 목록) */
    val plannedTotals: Counts
        get() = stops.flatMap { it.cargo }.groupingBy { it.kind }.fold(0) { acc, l -> acc + l.plannedQty }

    /** kind → 화면 이름 (예: box → 박스) */
    val kindNames: Map<String, String>
        get() = stops.flatMap { it.cargo }.associate { it.kind to it.itemName }
}

enum class TripStatus { PLANNED, IN_PROGRESS, COMPLETED, UNKNOWN }

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
) {
    val plannedCounts: Counts get() = cargo.groupingBy { it.kind }.fold(0) { acc, l -> acc + l.plannedQty }
}

enum class StopStatus { PENDING, COMPLETED, PARTIAL, UNKNOWN }

data class CargoLine(val id: Int, val itemName: String, val kind: String, val plannedQty: Int, val deliveredQty: Int)

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

data class ObservationInfo(
    val id: Int,
    val source: ObservationSource,
    val available: Boolean,
    val counts: Counts?,
    val observedAt: String,
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

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

sealed interface ApiError {
    /** 서버에 닿지 못함. 같은 요청 ID 로 다시 보내면 중복 반영 없이 재시도된다 */
    data class Network(val reason: String) : ApiError
    /** 서버 오류 응답 {"detail":{"code","message"}} */
    data class Http(val status: Int, val code: String?, val message: String) : ApiError
    data object NotConfigured : ApiError
}

interface TripRepository {
    suspend fun listTrips(): ApiResult<List<TripSummary>>
    suspend fun getTrip(tripId: String): ApiResult<Trip>
    suspend fun recordObservation(
        tripId: String,
        idempotencyKey: String,
        source: ObservationSource,
        /** null 이면 확인 불가(카메라 가림 등) */
        counts: Counts?,
        observedAtIso: String,
    ): ApiResult<Comparison>
    suspend fun confirmBaseline(tripId: String, idempotencyKey: String, counts: Counts, observationId: Int?): ApiResult<Trip>
    suspend fun completeStop(
        tripId: String,
        stopId: String,
        idempotencyKey: String,
        delivered: Counts,
        driverConfirmed: Boolean,
    ): ApiResult<CompleteResult>
}
