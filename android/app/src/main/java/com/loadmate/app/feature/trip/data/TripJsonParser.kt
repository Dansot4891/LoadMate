package com.loadmate.app.feature.trip.data

import com.loadmate.app.feature.trip.data.dto.*
import com.loadmate.app.feature.trip.data.mapper.toDomain
import com.loadmate.app.feature.trip.domain.*
import kotlinx.serialization.json.Json

/** 서버 응답을 DTO로 읽은 뒤 mapper에서 도메인으로 변환한다. */
internal object TripJsonParser {
    private val json = Json { ignoreUnknownKeys = true }
    fun tripList(body: String): List<TripSummary> = json.decodeFromString<TripListDto>(body).trips.map { it.toDomain() }
    fun trip(body: String): Trip = json.decodeFromString<TripDto>(body).toDomain()
    fun changedTrip(body: String): Trip = json.decodeFromString<TripEnvelopeDto>(body).trip.toDomain()
    fun history(body: String): List<HistoryItem> = json.decodeFromString<HistoryListDto>(body).items.map { it.toDomain() }
    fun observationComparison(body: String): Comparison = json.decodeFromString<ComparisonEnvelopeDto>(body).comparison.toDomain()
    fun baselineTrip(body: String): Trip = changedTrip(body)
    fun addressFound(body: String): Boolean = json.decodeFromString<AddressCheckDto>(body).found
    fun routePoints(body: String): RoutePoints = json.decodeFromString<RoutePointsDto>(body).toDomain()
    fun completeResult(body: String): CompleteResult = json.decodeFromString<CompleteResultDto>(body).toDomain()
}
