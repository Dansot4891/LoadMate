package com.loadmate.app.feature.trip.data.mapper

import com.loadmate.app.feature.trip.data.dto.*
import com.loadmate.app.feature.trip.domain.*

internal fun TripSummaryDto.toDomain() = TripSummary(id, name, tripStatus(status), dataSource, stopCount)
internal fun TripDto.toDomain() = Trip(id, name, dataSource, tripStatus(status), baseline?.toDomain(), stops.map { it.toDomain() }, expectedRemaining,
    comparison.toDomain(), driverId, vehicleId, endReason, pickups.map { it.toDomain() }, issues.map { it.toDomain() })
internal fun BaselineDto.toDomain() = Baseline(counts, confirmedAt, observationId)
internal fun StopDto.toDomain() = Stop(id, seq, label, name, address, latitude, longitude, stopStatus(status), resolution,
    cargo.map { it.toDomain() }, placeKey, cancelReason)
internal fun CargoLineDto.toDomain() = CargoLine(id, itemName, kind, plannedQty, deliveredQty, zone)
internal fun PickupDto.toDomain() = Pickup(id, stopId, itemName, kind, qty, returnPlace, status == "loaded")
internal fun IssueDto.toDomain() = Issue(id, kind, zone, note, sourceOf(source), observedAt, status == "open", resolution)
internal fun HistoryItemDto.toDomain() = HistoryItem(type, source, actorId, createdAt, summary)
internal fun ComparisonDto.toDomain() = Comparison(comparisonState(state), expected, observed, differences.map { it.toDomain() }, observation?.toDomain(), countOnly, staleAfterSeconds)
internal fun DifferenceDto.toDomain() = Difference(kind, expected, observed)
internal fun ObservationDto.toDomain() = ObservationInfo(id, sourceOf(source), status == "ok", counts, observedAt, zones)
internal fun CompleteResultDto.toDomain() = CompleteResult(result == "completed", replayed, stop.toDomain(), expectedRemaining, comparison.toDomain())
private fun sourceOf(value: String) = ObservationSource.entries.firstOrNull { it.wire == value } ?: ObservationSource.UNKNOWN
private fun tripStatus(value: String) = when (value) {
    "planned" -> TripStatus.PLANNED; "in_progress" -> TripStatus.IN_PROGRESS; "completed" -> TripStatus.COMPLETED
    "ended_incomplete" -> TripStatus.ENDED_INCOMPLETE; else -> TripStatus.UNKNOWN
}
private fun stopStatus(value: String) = when (value) {
    "pending" -> StopStatus.PENDING; "completed" -> StopStatus.COMPLETED; "partial" -> StopStatus.PARTIAL
    "cancelled" -> StopStatus.CANCELLED; else -> StopStatus.UNKNOWN
}
private fun comparisonState(value: String) = when (value) {
    "match" -> ComparisonState.MATCH; "mismatch" -> ComparisonState.MISMATCH; "no_observation" -> ComparisonState.NO_OBSERVATION
    "unavailable" -> ComparisonState.UNAVAILABLE; "stale" -> ComparisonState.STALE; else -> ComparisonState.UNKNOWN
}
