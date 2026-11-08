package com.loadmate.app.feature.fleet.data

import com.loadmate.app.core.network.ApiClient
import com.loadmate.app.feature.fleet.domain.Driver
import com.loadmate.app.feature.fleet.domain.Equipment
import com.loadmate.app.feature.fleet.domain.FleetRepository
import com.loadmate.app.feature.fleet.domain.Handover
import com.loadmate.app.feature.fleet.domain.HandoverPreview
import org.json.JSONArray
import org.json.JSONObject

class HttpFleetRepository(private val api: ApiClient) : FleetRepository {
    override suspend fun drivers() = api.get("/drivers") { b ->
        JSONObject(b).getJSONArray("drivers").objs().map { Driver(it.getString("id"), it.getString("name")) }
    }

    override suspend fun equipment(vehicleId: String) = api.get("/vehicles/$vehicleId/equipment") { b ->
        JSONObject(b).getJSONArray("equipment").objs().map(::equipment)
    }

    override suspend fun setEquipmentStatus(id: String, status: String, note: String?) =
        api.post("/equipment/$id/status", JSONObject().put("status", status).apply { note?.let { put("note", it) } }) { equipment(JSONObject(it)) }

    override suspend fun handoverPreview(tripId: String) = api.get("/trips/$tripId/handover/preview") { b ->
        val o = JSONObject(b)
        HandoverPreview(o.getJSONArray("summary_lines").strings(), o.getString("summary_method"), o.optString("current_driver_id").ifBlank { null })
    }

    override suspend fun latestHandover(tripId: String) = api.get("/trips/$tripId/handover") { b ->
        JSONObject(b).optJSONObject("handover")?.let(::handover)
    }

    override suspend fun createHandover(tripId: String, key: String, toDriverId: String, note: String) =
        api.post("/trips/$tripId/handover", JSONObject().put("idempotency_key", key).put("to_driver_id", toDriverId).put("note", note)) {
            handover(JSONObject(it).getJSONObject("handover"))
        }

    override suspend fun acceptHandover(id: Int) = api.post("/handovers/$id/accept", JSONObject()) { handover(JSONObject(it).getJSONObject("handover")) }

    private fun equipment(o: JSONObject) = Equipment(o.getString("id"), o.getString("name"), o.getString("location"), o.getString("status"),
        o.optString("note"), o.optString("updated_by").ifBlank { null }.takeUnless { o.isNull("updated_by") })

    private fun handover(o: JSONObject) = Handover(o.getInt("id"), o.getString("from_driver_id"), o.getString("to_driver_id"), o.optString("note"),
        o.getString("status") == "accepted", o.getJSONObject("summary").getJSONArray("summary_lines").strings())

    private fun JSONArray.objs() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}
