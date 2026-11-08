package com.loadmate.app.feature.trip.data

import com.loadmate.app.feature.trip.domain.Baseline
import com.loadmate.app.feature.trip.domain.CargoLine
import com.loadmate.app.feature.trip.domain.Comparison
import com.loadmate.app.feature.trip.domain.ComparisonState
import com.loadmate.app.feature.trip.domain.CompleteResult
import com.loadmate.app.feature.trip.domain.Counts
import com.loadmate.app.feature.trip.domain.Difference
import com.loadmate.app.feature.trip.domain.HistoryItem
import com.loadmate.app.feature.trip.domain.Issue
import com.loadmate.app.feature.trip.domain.Pickup
import com.loadmate.app.feature.trip.domain.ObservationInfo
import com.loadmate.app.feature.trip.domain.ObservationSource
import com.loadmate.app.feature.trip.domain.Stop
import com.loadmate.app.feature.trip.domain.StopStatus
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.domain.TripStatus
import com.loadmate.app.feature.trip.domain.TripSummary
import org.json.JSONArray
import org.json.JSONObject

/** 운행 API JSON → 앱 모델. 계약: docs/api-contract.md "운행". 형식 오류는 JSONException 으로 전파. */
internal object TripJsonParser {

    fun tripList(body: String): List<TripSummary> =
        JSONObject(body).getJSONArray("trips").objects().map {
            TripSummary(it.getString("id"), it.getString("name"), tripStatus(it.getString("status")), it.getString("data_source"), it.getInt("stop_count"))
        }

    fun trip(body: String): Trip = trip(JSONObject(body))

    fun trip(o: JSONObject): Trip = Trip(
        id = o.getString("id"),
        name = o.getString("name"),
        dataSource = o.getString("data_source"),
        status = tripStatus(o.getString("status")),
        baseline = o.optJSONObject("baseline")?.let {
            Baseline(counts(it.getJSONObject("counts")), it.getString("confirmed_at"), it.optIntOrNull("observation_id"))
        },
        stops = o.getJSONArray("stops").objects().map(::stop),
        expectedRemaining = o.optJSONObject("expected_remaining")?.let(::counts),
        comparison = comparison(o.getJSONObject("comparison")),
        driverId = o.optStringOrNull("driver_id"),
        vehicleId = o.optStringOrNull("vehicle_id"),
        endReason = o.optStringOrNull("end_reason"),
        pickups = o.optJSONArray("pickups")?.objects().orEmpty().map {
            Pickup(it.getInt("id"), it.optStringOrNull("stop_id"), it.getString("item_name"), it.getString("kind"), it.getInt("qty"),
                it.getString("return_place"), it.getString("status") == "loaded")
        },
        issues = o.optJSONArray("issues")?.objects().orEmpty().map {
            Issue(it.getInt("id"), it.getString("kind"), it.optStringOrNull("zone"), it.optString("note"), source(it.getString("source")),
                it.getString("observed_at"), it.getString("status") == "open", it.optStringOrNull("resolution"))
        },
    )

    fun changedTrip(body: String): Trip = trip(JSONObject(body).getJSONObject("trip"))

    fun history(body: String): List<HistoryItem> = JSONObject(body).getJSONArray("items").objects().map {
        HistoryItem(it.getString("type"), it.getString("source"), it.optStringOrNull("actor_id"), it.getString("created_at"), it.getString("summary"))
    }

    private fun source(s: String) = ObservationSource.entries.firstOrNull { it.wire == s } ?: ObservationSource.UNKNOWN

    fun observationComparison(body: String): Comparison = comparison(JSONObject(body).getJSONObject("comparison"))

    fun baselineTrip(body: String): Trip = trip(JSONObject(body).getJSONObject("trip"))

    fun completeResult(body: String): CompleteResult {
        val o = JSONObject(body)
        return CompleteResult(
            completed = o.getString("result") == "completed",
            replayed = o.optBoolean("replayed", false),
            stop = stop(o.getJSONObject("stop")),
            expectedRemaining = counts(o.getJSONObject("expected_remaining")),
            comparison = comparison(o.getJSONObject("comparison")),
        )
    }


    private fun stop(o: JSONObject) = Stop(
        id = o.getString("id"),
        seq = o.getInt("seq"),
        label = o.getString("label"),
        name = o.getString("name"),
        address = o.optString("address"),
        latitude = o.getDouble("latitude"),
        longitude = o.getDouble("longitude"),
        status = when (o.getString("status")) {
            "pending" -> StopStatus.PENDING
            "completed" -> StopStatus.COMPLETED
            "partial" -> StopStatus.PARTIAL
            "cancelled" -> StopStatus.CANCELLED
            else -> StopStatus.UNKNOWN
        },
        resolution = o.optStringOrNull("resolution"),
        cargo = o.getJSONArray("cargo").objects().map {
            CargoLine(it.getInt("id"), it.getString("item_name"), it.getString("kind"), it.getInt("planned_qty"), it.getInt("delivered_qty"), it.optStringOrNull("zone"))
        },
        placeKey = o.optString("place_key"),
        cancelReason = o.optStringOrNull("cancel_reason"),
    )

    private fun comparison(o: JSONObject) = Comparison(
        state = when (o.getString("state")) {
            "match" -> ComparisonState.MATCH
            "mismatch" -> ComparisonState.MISMATCH
            "no_observation" -> ComparisonState.NO_OBSERVATION
            "unavailable" -> ComparisonState.UNAVAILABLE
            "stale" -> ComparisonState.STALE
            else -> ComparisonState.UNKNOWN
        },
        expected = o.optJSONObject("expected")?.let(::counts),
        observed = o.optJSONObject("observed")?.let(::counts),
        differences = o.getJSONArray("differences").objects().map { Difference(it.getString("kind"), it.getInt("expected"), it.getInt("observed")) },
        observation = o.optJSONObject("observation")?.let {
            ObservationInfo(
                id = it.getInt("id"),
                source = source(it.getString("source")),
                available = it.getString("status") == "ok",
                counts = it.optJSONObject("counts")?.let(::counts),
                observedAt = it.getString("observed_at"),
                zones = it.optJSONObject("zones")?.let { z -> z.keys().asSequence().associateWith { k -> counts(z.getJSONObject(k)) } },
            )
        },
        countOnly = o.optBoolean("count_only", true),
        staleAfterSeconds = o.optInt("stale_after_seconds", 300),
    )

    private fun tripStatus(s: String) = when (s) {
        "planned" -> TripStatus.PLANNED
        "in_progress" -> TripStatus.IN_PROGRESS
        "completed" -> TripStatus.COMPLETED
        "ended_incomplete" -> TripStatus.ENDED_INCOMPLETE
        else -> TripStatus.UNKNOWN
    }

    private fun counts(o: JSONObject): Counts = o.keys().asSequence().associateWith { o.getInt(it) }

    fun countsJson(c: Counts): JSONObject = JSONObject().apply { c.forEach { (k, v) -> put(k, v) } }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.optStringOrNull(k: String): String? = if (isNull(k)) null else optString(k)
    private fun JSONObject.optIntOrNull(k: String): Int? = if (isNull(k) || !has(k)) null else getInt(k)
}
