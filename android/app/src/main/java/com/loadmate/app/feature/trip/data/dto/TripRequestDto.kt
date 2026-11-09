package com.loadmate.app.feature.trip.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class ObservationRequestDto(
    @SerialName("idempotency_key") val key: String, val source: String, val status: String,
    val counts: Map<String, Int>?, @SerialName("observed_at") val observedAt: String,
    val zones: Map<String, Map<String, Int>>?,
)
@Serializable data class BaselineRequestDto(@SerialName("idempotency_key") val key: String, val counts: Map<String, Int>, @SerialName("observation_id") val observationId: Int?)
@Serializable data class CompleteRequestDto(@SerialName("idempotency_key") val key: String, val delivered: Map<String, Int>, val resolution: String?)
@Serializable data class NewCargoDto(@SerialName("item_name") val itemName: String, val kind: String, val qty: Int)
@Serializable data class AddStopDto(val name: String, val address: String, val latitude: Double?, val longitude: Double?, val cargo: List<NewCargoDto>)
@Serializable data class ChangeStopPlaceDto(val name: String, val address: String)
@Serializable data class ReorderStopsDto(@SerialName("stop_ids") val stopIds: List<String>)
@Serializable data class CancelStopDto(val reason: String)
@Serializable data class ChangeCargoDto(@SerialName("cargo_line_id") val cargoLineId: Int, @SerialName("planned_qty") val plannedQty: Int)
@Serializable data class EndTripDto(val reason: String?)
@Serializable data class AddPickupDto(@SerialName("stop_id") val stopId: String?, @SerialName("item_name") val itemName: String, val kind: String, val qty: Int, @SerialName("return_place") val returnPlace: String)
@Serializable data class RecordIssueDto(val kind: String, val zone: String?, val note: String, @SerialName("observation_source") val source: String, @SerialName("observed_at") val observedAt: String)
@Serializable data class ResolveIssueDto(val resolution: String, val note: String?)
@Serializable data class SlotDto(val zone: String, val lane: Int, val layer: Int)
@Serializable data class PlacementEventDto(@SerialName("unit_id") val unitId: Int, val type: String, val slot: SlotDto?)
@Serializable data class PlacementsDto(@SerialName("observed_at") val observedAt: String, val events: List<PlacementEventDto>)
@Serializable class EmptyRequestDto
