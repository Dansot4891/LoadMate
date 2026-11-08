package com.loadmate.app.feature.records.data

import com.loadmate.app.core.network.ApiClient
import com.loadmate.app.feature.records.domain.Memo
import com.loadmate.app.feature.records.domain.MemoRepository
import com.loadmate.app.feature.records.domain.NewMemo
import com.loadmate.app.core.network.ApiResult
import org.json.JSONArray
import org.json.JSONObject
import com.loadmate.app.feature.records.data.dto.MemoDto
import com.loadmate.app.feature.records.data.dto.MemoEnvelopeDto
import com.loadmate.app.feature.records.data.dto.MemoListDto
import com.loadmate.app.feature.records.data.mapper.toDomain
import kotlinx.serialization.json.Json

class MemoRepositoryImpl(private val api: ApiClient) : MemoRepository {
    override suspend fun list(scopeType: String?, scopeId: String?, tripId: String?, query: String?) =
        api.get("/memos", mapOf("scope_type" to scopeType, "scope_id" to scopeId, "trip_id" to tripId, "q" to query), ::memos)

    override suspend fun forStop(tripId: String, stopId: String) = api.get("/trips/$tripId/stops/$stopId/memos", parse = ::memos)

    override suspend fun create(key: String, memo: NewMemo): ApiResult<Memo> = api.post(
        "/memos",
        JSONObject().put("idempotency_key", key).put("scope_type", memo.scopeType).put("scope_id", memo.scopeId).put("body", memo.body)
            .put("tags", JSONArray(memo.tags)).put("visibility", if (memo.shared) "shared" else "private")
            .put("persistence", if (memo.thisTripOnly) "this_trip" else "always").put("trip_id", memo.tripId ?: JSONObject.NULL)
            .put("source", memo.source),
    ) { json.decodeFromString<MemoEnvelopeDto>(it).memo.toDomain() }

    override suspend fun update(id: Int, body: String?, shared: Boolean?): ApiResult<Memo> = api.put(
        "/memos/$id",
        JSONObject().apply {
            body?.let { put("body", it) }
            shared?.let { put("visibility", if (it) "shared" else "private") }
        },
    ) { json.decodeFromString<MemoDto>(it).toDomain() }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun memos(body: String): List<Memo> = json.decodeFromString<MemoListDto>(body).memos.map { it.toDomain() }
    }
}
