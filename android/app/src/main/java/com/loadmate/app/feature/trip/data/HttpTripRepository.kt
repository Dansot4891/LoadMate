package com.loadmate.app.feature.trip.data

import com.loadmate.app.core.network.ApiClient
import com.loadmate.app.core.network.ApiResult
import com.loadmate.app.feature.trip.domain.Comparison
import com.loadmate.app.feature.trip.domain.CompleteResult
import com.loadmate.app.feature.trip.domain.Counts
import com.loadmate.app.feature.trip.domain.HistoryItem
import com.loadmate.app.feature.trip.domain.NewCargo
import com.loadmate.app.feature.trip.domain.ObservationSource
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.domain.TripRepository
import com.loadmate.app.feature.trip.domain.TripSummary
import org.json.JSONArray
import org.json.JSONObject

/** 운행 API. 계약: docs/api-contract.md "운행", "운행 관리·회수·이상·이력" */
class HttpTripRepository(private val api: ApiClient) : TripRepository {

    override suspend fun listTrips(): ApiResult<List<TripSummary>> = api.get("/trips", parse = TripJsonParser::tripList)

    override suspend fun getTrip(tripId: String): ApiResult<Trip> = api.get("/trips/$tripId", parse = TripJsonParser::trip)

    override suspend fun recordObservation(
        tripId: String, idempotencyKey: String, source: ObservationSource, counts: Counts?, observedAtIso: String, zones: Map<String, Counts>?,
    ): ApiResult<Comparison> = api.post(
        "/trips/$tripId/observations",
        JSONObject()
            .put("idempotency_key", idempotencyKey)
            .put("source", source.wire)
            .put("status", if (counts == null) "unavailable" else "ok")
            .put("counts", counts?.let(::countsJson) ?: JSONObject.NULL)
            .put("observed_at", observedAtIso)
            .put("zones", zones?.let { z -> JSONObject().apply { z.forEach { (k, v) -> put(k, countsJson(v)) } } } ?: JSONObject.NULL),
        TripJsonParser::observationComparison,
    )

    override suspend fun confirmBaseline(tripId: String, idempotencyKey: String, counts: Counts, observationId: Int?): ApiResult<Trip> =
        api.post(
            "/trips/$tripId/baseline",
            JSONObject().put("idempotency_key", idempotencyKey).put("counts", countsJson(counts)).put("observation_id", observationId ?: JSONObject.NULL),
            TripJsonParser::baselineTrip,
        )

    override suspend fun completeStop(
        tripId: String, stopId: String, idempotencyKey: String, delivered: Counts, driverConfirmed: Boolean,
    ): ApiResult<CompleteResult> = api.post(
        "/trips/$tripId/stops/$stopId/complete",
        JSONObject().put("idempotency_key", idempotencyKey).put("delivered", countsJson(delivered))
            .put("resolution", if (driverConfirmed) "driver_confirmed" else JSONObject.NULL),
        TripJsonParser::completeResult,
    )

    override suspend fun addStop(tripId: String, key: String, name: String, address: String, lat: Double, lng: Double, cargo: List<NewCargo>) =
        change("/trips/$tripId/stops", key, JSONObject().put("name", name).put("address", address).put("latitude", lat).put("longitude", lng)
            .put("cargo", JSONArray(cargo.map { JSONObject().put("item_name", it.itemName).put("kind", it.kind).put("qty", it.qty) })))

    override suspend fun reorderStops(tripId: String, key: String, stopIds: List<String>) =
        change("/trips/$tripId/stops/reorder", key, JSONObject().put("stop_ids", JSONArray(stopIds)))

    override suspend fun cancelStop(tripId: String, stopId: String, key: String, reason: String) =
        change("/trips/$tripId/stops/$stopId/cancel", key, JSONObject().put("reason", reason))

    override suspend fun changeCargoQty(tripId: String, stopId: String, key: String, cargoLineId: Int, plannedQty: Int) =
        change("/trips/$tripId/stops/$stopId/cargo", key, JSONObject().put("cargo_line_id", cargoLineId).put("planned_qty", plannedQty))

    override suspend fun endTrip(tripId: String, key: String, reason: String?) =
        change("/trips/$tripId/end", key, JSONObject().put("reason", reason ?: JSONObject.NULL))

    override suspend fun addPickup(tripId: String, key: String, stopId: String?, itemName: String, kind: String, qty: Int, returnPlace: String, source: String) =
        change("/trips/$tripId/pickups", key, JSONObject().put("stop_id", stopId ?: JSONObject.NULL).put("item_name", itemName).put("kind", kind)
            .put("qty", qty).put("return_place", returnPlace), source)

    override suspend fun returnPickup(tripId: String, pickupId: Int, key: String) =
        change("/trips/$tripId/pickups/$pickupId/return", key, JSONObject())

    override suspend fun recordIssue(tripId: String, key: String, kind: String, zone: String?, note: String, source: ObservationSource, observedAtIso: String) =
        change("/trips/$tripId/issues", key, JSONObject().put("kind", kind).put("zone", zone ?: JSONObject.NULL).put("note", note)
            .put("observation_source", source.wire).put("observed_at", observedAtIso))

    override suspend fun resolveIssue(tripId: String, issueId: Int, key: String, resolution: String, note: String?) =
        change("/trips/$tripId/issues/$issueId/resolve", key, JSONObject().put("resolution", resolution).put("note", note ?: JSONObject.NULL))

    override suspend fun history(tripId: String): ApiResult<List<HistoryItem>> = api.get("/trips/$tripId/history", parse = TripJsonParser::history)

    private suspend fun change(path: String, key: String, body: JSONObject, source: String = "screen"): ApiResult<Trip> =
        api.post(path, body.put("idempotency_key", key).put("source", source), TripJsonParser::changedTrip)

    private fun countsJson(c: Counts): JSONObject = JSONObject().apply { c.forEach { (k, v) -> put(k, v) } }
}
