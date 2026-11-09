package com.loadmate.app.feature.trip.data

import com.loadmate.app.core.network.ApiResult
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.domain.TripRepository
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 이번 실행 중 마지막으로 받은 운행을 메모리에 보관한다 (R11·F66: 통신 실패 시 최근 목록 유지).
 * 캐시는 서버에서 받은 실제 값이며, 화면은 "오프라인 · 마지막 수신 시각"으로 구분해 표시한다 (원칙 5).
 * 상태 변경은 캐시로 처리하지 않는다 → 재연결 후 같은 요청 ID 로 다시 보낸다.
 */
class CachingTripRepository(private val inner: TripRepository) : TripRepository by inner {
    private val memory = mutableMapOf<String, Pair<Trip, Instant>>()
    private val mutableCurrentTrip = MutableStateFlow<Trip?>(null)
    override val currentTrip: StateFlow<Trip?> = mutableCurrentTrip

    private fun remember(r: ApiResult<Trip>): ApiResult<Trip> {
        if (r is ApiResult.Success) {
            memory[r.value.id] = r.value to Instant.now()
            mutableCurrentTrip.value = r.value
        }
        return r
    }

    override suspend fun getTrip(tripId: String): ApiResult<Trip> = remember(inner.getTrip(tripId))

    override suspend fun listTrips() = inner.listTrips()
    override suspend fun recordObservation(tripId: String, idempotencyKey: String, source: com.loadmate.app.feature.trip.domain.ObservationSource,
        counts: com.loadmate.app.feature.trip.domain.Counts?, observedAtIso: String, zones: Map<String, com.loadmate.app.feature.trip.domain.Counts>?) =
        inner.recordObservation(tripId, idempotencyKey, source, counts, observedAtIso, zones)
    override suspend fun confirmBaseline(tripId: String, idempotencyKey: String, counts: com.loadmate.app.feature.trip.domain.Counts, observationId: Int?) =
        remember(inner.confirmBaseline(tripId, idempotencyKey, counts, observationId))
    override suspend fun completeStop(tripId: String, stopId: String, idempotencyKey: String, delivered: com.loadmate.app.feature.trip.domain.Counts, driverConfirmed: Boolean) =
        inner.completeStop(tripId, stopId, idempotencyKey, delivered, driverConfirmed)
    override suspend fun checkAddress(address: String) = inner.checkAddress(address)
    override suspend fun changeStopPlace(tripId: String, stopId: String, key: String, name: String, address: String) =
        remember(inner.changeStopPlace(tripId, stopId, key, name, address))
    override suspend fun routePoints(tripId: String, stopIds: List<String>) = inner.routePoints(tripId, stopIds)
    override suspend fun addStop(tripId: String, key: String, name: String, address: String, lat: Double?, lng: Double?, cargo: List<com.loadmate.app.feature.trip.domain.NewCargo>) =
        remember(inner.addStop(tripId, key, name, address, lat, lng, cargo))
    override suspend fun reorderStops(tripId: String, key: String, stopIds: List<String>) = remember(inner.reorderStops(tripId, key, stopIds))
    override suspend fun cancelStop(tripId: String, stopId: String, key: String, reason: String) = remember(inner.cancelStop(tripId, stopId, key, reason))
    override suspend fun changeCargoQty(tripId: String, stopId: String, key: String, cargoLineId: Int, plannedQty: Int) =
        remember(inner.changeCargoQty(tripId, stopId, key, cargoLineId, plannedQty))
    override suspend fun endTrip(tripId: String, key: String, reason: String?) = remember(inner.endTrip(tripId, key, reason))
    override suspend fun addPickup(tripId: String, key: String, stopId: String?, itemName: String, kind: String, qty: Int, returnPlace: String, source: String) =
        remember(inner.addPickup(tripId, key, stopId, itemName, kind, qty, returnPlace, source))
    override suspend fun returnPickup(tripId: String, pickupId: Int, key: String) = remember(inner.returnPickup(tripId, pickupId, key))
    override suspend fun recordIssue(tripId: String, key: String, kind: String, zone: String?, note: String,
        source: com.loadmate.app.feature.trip.domain.ObservationSource, observedAtIso: String) =
        remember(inner.recordIssue(tripId, key, kind, zone, note, source, observedAtIso))
    override suspend fun resolveIssue(tripId: String, issueId: Int, key: String, resolution: String, note: String?) =
        remember(inner.resolveIssue(tripId, issueId, key, resolution, note))
    override suspend fun history(tripId: String) = inner.history(tripId)
    override suspend fun recordPlacements(tripId: String, key: String, source: com.loadmate.app.feature.trip.domain.ObservationSource, observedAtIso: String,
        events: List<com.loadmate.app.feature.trip.domain.PlacementEvent>) = remember(inner.recordPlacements(tripId, key, source, observedAtIso, events))

    /** 이번 실행 중 마지막으로 받은 운행과 수신 시각 */
    fun cached(tripId: String): Pair<Trip, Instant>? = memory[tripId]
}
