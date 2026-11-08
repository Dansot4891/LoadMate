package com.loadmate.app.feature.fleet.data

import com.loadmate.app.core.network.ApiClient
import com.loadmate.app.feature.fleet.domain.Driver
import com.loadmate.app.feature.fleet.domain.Equipment
import com.loadmate.app.feature.fleet.domain.FleetRepository
import com.loadmate.app.feature.fleet.domain.Handover
import com.loadmate.app.feature.fleet.domain.HandoverPreview
import kotlinx.serialization.encodeToString

import com.loadmate.app.feature.fleet.data.dto.*
import com.loadmate.app.feature.fleet.data.mapper.toDomain
import kotlinx.serialization.json.Json

class FleetRepositoryImpl(private val api: ApiClient) : FleetRepository {
    override suspend fun drivers() = api.get("/drivers") { b ->
        json.decodeFromString<DriversDto>(b).drivers.map { it.toDomain() }
    }

    override suspend fun equipment(vehicleId: String) = api.get("/vehicles/$vehicleId/equipment") { b ->
        json.decodeFromString<EquipmentListDto>(b).equipment.map { it.toDomain() }
    }

    override suspend fun setEquipmentStatus(id: String, status: String, note: String?) =
        api.post("/equipment/$id/status", json.encodeToString(EquipmentStatusRequestDto(status, note))) { json.decodeFromString<EquipmentDto>(it).toDomain() }

    override suspend fun handoverPreview(tripId: String) = api.get("/trips/$tripId/handover/preview") { b ->
        json.decodeFromString<HandoverPreviewDto>(b).toDomain()
    }

    override suspend fun latestHandover(tripId: String) = api.get("/trips/$tripId/handover") { b ->
        json.decodeFromString<HandoverEnvelopeDto>(b).handover?.toDomain()
    }

    override suspend fun createHandover(tripId: String, key: String, toDriverId: String, note: String) =
        api.post("/trips/$tripId/handover", json.encodeToString(CreateHandoverRequestDto(key, toDriverId, note))) {
            json.decodeFromString<HandoverEnvelopeDto>(it).handover!!.toDomain()
        }

    override suspend fun acceptHandover(id: Int) = api.post("/handovers/$id/accept", json.encodeToString(AcceptHandoverRequestDto())) { json.decodeFromString<HandoverEnvelopeDto>(it).handover!!.toDomain() }

    companion object { private val json = Json { ignoreUnknownKeys = true } }
}
