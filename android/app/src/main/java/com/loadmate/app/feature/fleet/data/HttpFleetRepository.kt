package com.loadmate.app.feature.fleet.data

import com.loadmate.app.core.network.ApiClient
import com.loadmate.app.feature.fleet.domain.Driver
import com.loadmate.app.feature.fleet.domain.Equipment
import com.loadmate.app.feature.fleet.domain.FleetRepository
import com.loadmate.app.feature.fleet.domain.Handover
import com.loadmate.app.feature.fleet.domain.HandoverPreview
import org.json.JSONArray
import org.json.JSONObject

import com.loadmate.app.feature.fleet.data.dto.*
import com.loadmate.app.feature.fleet.data.mapper.toDomain
import kotlinx.serialization.json.Json

class HttpFleetRepository(private val api: ApiClient) : FleetRepository {
    override suspend fun drivers() = api.get("/drivers") { b ->
        json.decodeFromString<DriversDto>(b).drivers.map { it.toDomain() }
    }

    override suspend fun equipment(vehicleId: String) = api.get("/vehicles/$vehicleId/equipment") { b ->
        json.decodeFromString<EquipmentListDto>(b).equipment.map { it.toDomain() }
    }

    override suspend fun setEquipmentStatus(id: String, status: String, note: String?) =
        api.post("/equipment/$id/status", JSONObject().put("status", status).apply { note?.let { put("note", it) } }) { json.decodeFromString<EquipmentDto>(it).toDomain() }

    override suspend fun handoverPreview(tripId: String) = api.get("/trips/$tripId/handover/preview") { b ->
        json.decodeFromString<HandoverPreviewDto>(b).toDomain()
    }

    override suspend fun latestHandover(tripId: String) = api.get("/trips/$tripId/handover") { b ->
        json.decodeFromString<HandoverEnvelopeDto>(b).handover?.toDomain()
    }

    override suspend fun createHandover(tripId: String, key: String, toDriverId: String, note: String) =
        api.post("/trips/$tripId/handover", JSONObject().put("idempotency_key", key).put("to_driver_id", toDriverId).put("note", note)) {
            json.decodeFromString<HandoverEnvelopeDto>(it).handover!!.toDomain()
        }

    override suspend fun acceptHandover(id: Int) = api.post("/handovers/$id/accept", JSONObject()) { json.decodeFromString<HandoverEnvelopeDto>(it).handover!!.toDomain() }

    companion object { private val json = Json { ignoreUnknownKeys = true } }
}
