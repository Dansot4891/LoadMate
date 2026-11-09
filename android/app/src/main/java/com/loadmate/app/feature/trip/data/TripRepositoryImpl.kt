package com.loadmate.app.feature.trip.data

import com.loadmate.app.core.network.ApiClient
import com.loadmate.app.core.network.ApiResult
import com.loadmate.app.feature.trip.data.dto.*
import com.loadmate.app.feature.trip.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 운행 API. 요청과 응답은 DTO로 직렬화하며 판정은 서버 응답을 따른다. */
class TripRepositoryImpl(private val api: ApiClient) : TripRepository {
    override val currentTrip = MutableStateFlow<Trip?>(null)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun listTrips(): ApiResult<List<TripSummary>> = api.get("/trips", parse = TripJsonParser::tripList)
    override suspend fun getTrip(tripId: String): ApiResult<Trip> = api.get("/trips/$tripId", parse = TripJsonParser::trip)

    override suspend fun recordObservation(tripId: String, idempotencyKey: String, source: ObservationSource,
        counts: Counts?, observedAtIso: String, zones: Map<String, Counts>?): ApiResult<Comparison> =
        api.post("/trips/$tripId/observations", json.encodeToString(ObservationRequestDto(idempotencyKey, source.wire,
            if (counts == null) "unavailable" else "ok", counts, observedAtIso, zones)), TripJsonParser::observationComparison)

    override suspend fun confirmBaseline(tripId: String, idempotencyKey: String, counts: Counts, observationId: Int?): ApiResult<Trip> =
        api.post("/trips/$tripId/baseline", json.encodeToString(BaselineRequestDto(idempotencyKey, counts, observationId)), TripJsonParser::baselineTrip)

    override suspend fun completeStop(tripId: String, stopId: String, idempotencyKey: String, delivered: Counts, driverConfirmed: Boolean): ApiResult<CompleteResult> =
        api.post("/trips/$tripId/stops/$stopId/complete", json.encodeToString(CompleteRequestDto(idempotencyKey, delivered,
            if (driverConfirmed) "driver_confirmed" else null)), TripJsonParser::completeResult)

    override suspend fun checkAddress(address: String): ApiResult<Boolean> = api.get("/places/check", mapOf("address" to address), TripJsonParser::addressFound)
    override suspend fun changeStopPlace(tripId: String, stopId: String, key: String, name: String, address: String) =
        change("/trips/$tripId/stops/$stopId/place", key, ChangeStopPlaceDto(name, address))
    override suspend fun routePoints(tripId: String, stopIds: List<String>): ApiResult<RoutePoints> =
        api.get("/trips/$tripId/route-points", mapOf("stop_ids" to stopIds.joinToString(",")), TripJsonParser::routePoints)

    override suspend fun addStop(tripId: String, key: String, name: String, address: String, lat: Double?, lng: Double?, cargo: List<NewCargo>) =
        change("/trips/$tripId/stops", key, AddStopDto(name, address, lat, lng, cargo.map { NewCargoDto(it.itemName, it.kind, it.qty) }))
    override suspend fun reorderStops(tripId: String, key: String, stopIds: List<String>) = change("/trips/$tripId/stops/reorder", key, ReorderStopsDto(stopIds))
    override suspend fun cancelStop(tripId: String, stopId: String, key: String, reason: String) = change("/trips/$tripId/stops/$stopId/cancel", key, CancelStopDto(reason))
    override suspend fun changeCargoQty(tripId: String, stopId: String, key: String, cargoLineId: Int, plannedQty: Int) =
        change("/trips/$tripId/stops/$stopId/cargo", key, ChangeCargoDto(cargoLineId, plannedQty))
    override suspend fun endTrip(tripId: String, key: String, reason: String?) = change("/trips/$tripId/end", key, EndTripDto(reason))
    override suspend fun addPickup(tripId: String, key: String, stopId: String?, itemName: String, kind: String, qty: Int, returnPlace: String, source: String) =
        change("/trips/$tripId/pickups", key, AddPickupDto(stopId, itemName, kind, qty, returnPlace), source)
    override suspend fun returnPickup(tripId: String, pickupId: Int, key: String) = change("/trips/$tripId/pickups/$pickupId/return", key, EmptyRequestDto())
    override suspend fun recordIssue(tripId: String, key: String, kind: String, zone: String?, note: String, source: ObservationSource, observedAtIso: String) =
        change("/trips/$tripId/issues", key, RecordIssueDto(kind, zone, note, source.wire, observedAtIso))
    override suspend fun resolveIssue(tripId: String, issueId: Int, key: String, resolution: String, note: String?) =
        change("/trips/$tripId/issues/$issueId/resolve", key, ResolveIssueDto(resolution, note))
    override suspend fun history(tripId: String): ApiResult<List<HistoryItem>> = api.get("/trips/$tripId/history", parse = TripJsonParser::history)

    override suspend fun recordPlacements(tripId: String, key: String, source: ObservationSource, observedAtIso: String, events: List<PlacementEvent>) =
        change("/trips/$tripId/placements", key, PlacementsDto(observedAtIso, events.map { e ->
            PlacementEventDto(e.unitId, e.type.wire, e.slot?.let { SlotDto(it.zone, it.lane, it.layer) })
        }), source.wire)

    private suspend inline fun <reified T> change(path: String, key: String, request: T, source: String = "screen"): ApiResult<Trip> {
        val payload = json.encodeToJsonElement(kotlinx.serialization.serializer<T>(), request) as JsonObject
        val body = JsonObject(payload + mapOf("idempotency_key" to JsonPrimitive(key), "source" to JsonPrimitive(source)))
        return api.post(path, body.toString(), TripJsonParser::changedTrip)
    }
}
