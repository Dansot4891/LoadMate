package com.loadmate.app.feature.records.data

import com.loadmate.app.core.network.ApiClient
import com.loadmate.app.feature.records.domain.Memo
import com.loadmate.app.feature.records.domain.MemoRepository
import com.loadmate.app.feature.records.domain.NewMemo
import com.loadmate.app.feature.trip.domain.ApiResult
import org.json.JSONArray
import org.json.JSONObject

class HttpMemoRepository(private val api: ApiClient) : MemoRepository {
    override suspend fun list(scopeType: String?, scopeId: String?, tripId: String?, query: String?) =
        api.get("/memos", mapOf("scope_type" to scopeType, "scope_id" to scopeId, "trip_id" to tripId, "q" to query), ::memos)

    override suspend fun forStop(tripId: String, stopId: String) = api.get("/trips/$tripId/stops/$stopId/memos", parse = ::memos)

    override suspend fun create(key: String, memo: NewMemo): ApiResult<Memo> = api.post(
        "/memos",
        JSONObject().put("idempotency_key", key).put("scope_type", memo.scopeType).put("scope_id", memo.scopeId).put("body", memo.body)
            .put("tags", JSONArray(memo.tags)).put("visibility", if (memo.shared) "shared" else "private")
            .put("persistence", if (memo.thisTripOnly) "this_trip" else "always").put("trip_id", memo.tripId ?: JSONObject.NULL)
            .put("source", memo.source),
    ) { memo(JSONObject(it).getJSONObject("memo")) }

    override suspend fun update(id: Int, body: String?, shared: Boolean?): ApiResult<Memo> = api.put(
        "/memos/$id",
        JSONObject().apply {
            body?.let { put("body", it) }
            shared?.let { put("visibility", if (it) "shared" else "private") }
        },
    ) { memo(JSONObject(it)) }

    companion object {
        fun memos(body: String): List<Memo> = JSONObject(body).getJSONArray("memos").let { a -> (0 until a.length()).map { memo(a.getJSONObject(it)) } }

        fun memo(o: JSONObject) = Memo(
            id = o.getInt("id"), scopeType = o.getString("scope_type"), scopeId = o.getString("scope_id"), body = o.getString("body"),
            tags = o.optJSONArray("tags")?.let { t -> (0 until t.length()).map { t.getString(it) } }.orEmpty(),
            shared = o.getString("visibility") == "shared", thisTripOnly = o.getString("persistence") == "this_trip",
            source = o.optString("source", "screen"), authorId = o.getString("author_id"), updatedAt = o.getString("updated_at"),
            updatedBy = o.getString("updated_by"),
        )
    }
}
