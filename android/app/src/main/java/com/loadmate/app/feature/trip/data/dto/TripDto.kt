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
)
@Serializable data class BaselineDto(val counts: Map<String, Int>, @SerialName("confirmed_at") val confirmedAt: String, @SerialName("observation_id") val observationId: Int? = null)
@Serializable data class StopDto(
    val id: String, val seq: Int, val label: String, val name: String, val address: String = "", val latitude: Double, val longitude: Double,
    val status: String, val resolution: String? = null, val cargo: List<CargoLineDto>, @SerialName("place_key") val placeKey: String = "",
    @SerialName("cancel_reason") val cancelReason: String? = null,
)
@Serializable data class CargoLineDto(val id: Int, @SerialName("item_name") val itemName: String, val kind: String, @SerialName("planned_qty") val plannedQty: Int, @SerialName("delivered_qty") val deliveredQty: Int, val zone: String? = null)
@Serializable data class PickupDto(val id: Int, @SerialName("stop_id") val stopId: String? = null, @SerialName("item_name") val itemName: String, val kind: String, val qty: Int, @SerialName("return_place") val returnPlace: String, val status: String)
@Serializable data class IssueDto(val id: Int, val kind: String, val zone: String? = null, val note: String = "", val source: String, @SerialName("observed_at") val observedAt: String, val status: String, val resolution: String? = null)
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
