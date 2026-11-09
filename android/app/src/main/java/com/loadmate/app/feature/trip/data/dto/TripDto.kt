package com.loadmate.app.feature.trip.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class TripListDto(val trips: List<TripSummaryDto>)
@Serializable data class TripSummaryDto(val id: String, val name: String, val status: String, @SerialName("data_source") val dataSource: String, @SerialName("stop_count") val stopCount: Int)
@Serializable data class TripEnvelopeDto(val trip: TripDto)
@Serializable data class TripDto(
    val id: String, val name: String, @SerialName("data_source") val dataSource: String, val status: String,
    val baseline: BaselineDto? = null, val stops: List<StopDto>, @SerialName("expected_remaining") val expectedRemaining: Map<String, Int>? = null,
    val comparison: ComparisonDto, @SerialName("driver_id") val driverId: String? = null,
    @SerialName("vehicle_id") val vehicleId: String? = null, @SerialName("end_reason") val endReason: String? = null,
    val pickups: List<PickupDto> = emptyList(), val issues: List<IssueDto> = emptyList(),
    val units: List<CargoUnitDto> = emptyList(),
)
@Serializable data class CargoUnitDto(
    val id: Int, @SerialName("stop_id") val stopId: String? = null, @SerialName("pickup_id") val pickupId: Int? = null,
    val kind: String, @SerialName("item_name") val itemName: String, val no: Int, val state: String, @SerialName("load_seq") val loadSeq: Int,
    val zone: String? = null, val lane: Int? = null, val layer: Int? = null,
    @SerialName("planned_zone") val plannedZone: String? = null, @SerialName("planned_lane") val plannedLane: Int? = null, @SerialName("planned_layer") val plannedLayer: Int? = null,
    @SerialName("position_source") val positionSource: String? = null,
)
@Serializable data class BaselineDto(val counts: Map<String, Int>, @SerialName("confirmed_at") val confirmedAt: String, @SerialName("observation_id") val observationId: Int? = null)
@Serializable data class StopDto(
    val id: String, val seq: Int, val label: String, val name: String, val address: String = "", val latitude: Double? = null, val longitude: Double? = null,
    val status: String, val resolution: String? = null, val cargo: List<CargoLineDto>, @SerialName("place_key") val placeKey: String = "",
    @SerialName("cancel_reason") val cancelReason: String? = null,
)
@Serializable data class AddressCheckDto(val found: Boolean)
@Serializable data class RoutePointDto(@SerialName("stop_id") val stopId: String, val latitude: Double, val longitude: Double)
@Serializable data class RoutePointsDto(val points: List<RoutePointDto>, val missing: List<String>)
@Serializable data class CargoLineDto(val id: Int, @SerialName("item_name") val itemName: String, val kind: String, @SerialName("planned_qty") val plannedQty: Int, @SerialName("delivered_qty") val deliveredQty: Int, val zone: String? = null)
@Serializable data class PickupDto(val id: Int, @SerialName("stop_id") val stopId: String? = null, @SerialName("item_name") val itemName: String, val kind: String, val qty: Int, @SerialName("return_place") val returnPlace: String, val status: String)
@Serializable data class IssueDto(val id: Int, val kind: String, val zone: String? = null, val note: String = "", val source: String, @SerialName("observed_at") val observedAt: String, val status: String, val resolution: String? = null, @SerialName("unit_id") val unitId: Int? = null)
@Serializable data class HistoryListDto(val items: List<HistoryItemDto>)
@Serializable data class HistoryItemDto(val type: String, val source: String, @SerialName("actor_id") val actorId: String? = null, @SerialName("created_at") val createdAt: String, val summary: String)
@Serializable data class ComparisonEnvelopeDto(val comparison: ComparisonDto)
@Serializable data class ComparisonDto(
    val state: String, val expected: Map<String, Int>? = null, val observed: Map<String, Int>? = null,
    val differences: List<DifferenceDto>, val observation: ObservationDto? = null,
    @SerialName("count_only") val countOnly: Boolean = true, @SerialName("stale_after_seconds") val staleAfterSeconds: Int = 300,
)
@Serializable data class DifferenceDto(val kind: String, val expected: Int, val observed: Int)
@Serializable data class ObservationDto(val id: Int, val source: String, val status: String, val counts: Map<String, Int>? = null, @SerialName("observed_at") val observedAt: String, val zones: Map<String, Map<String, Int>>? = null)
@Serializable data class CompleteResultDto(val result: String, val replayed: Boolean = false, val stop: StopDto, @SerialName("expected_remaining") val expectedRemaining: Map<String, Int>, val comparison: ComparisonDto)
