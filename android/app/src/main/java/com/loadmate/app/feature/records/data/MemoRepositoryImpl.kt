package com.loadmate.app.feature.records.data

import com.loadmate.app.core.network.ApiClient
import com.loadmate.app.core.network.ApiResult
import com.loadmate.app.feature.records.data.dto.*
import com.loadmate.app.feature.records.data.mapper.toDomain
import com.loadmate.app.feature.records.domain.Memo
import com.loadmate.app.feature.records.domain.MemoRepository
import com.loadmate.app.feature.records.domain.NewMemo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MemoRepositoryImpl(private val api: ApiClient) : MemoRepository {
    override suspend fun list(scopeType: String?, scopeId: String?, tripId: String?, query: String?) =
        api.get("/memos", mapOf("scope_type" to scopeType, "scope_id" to scopeId, "trip_id" to tripId, "q" to query), ::memos)
    override suspend fun forStop(tripId: String, stopId: String) = api.get("/trips/$tripId/stops/$stopId/memos", parse = ::memos)
    override suspend fun create(key: String, memo: NewMemo): ApiResult<Memo> = api.postWaitingForAi("/memos",
        json.encodeToString(CreateMemoDto(key, memo.scopeType, memo.scopeId, memo.body, memo.tags,
            if (memo.shared) "shared" else "private", if (memo.thisTripOnly) "this_trip" else "always", memo.tripId, memo.source, memo.category, memo.location))) {
        json.decodeFromString<MemoEnvelopeDto>(it).memo.toDomain()
    }
    override suspend fun update(id: Int, body: String?, shared: Boolean?, category: String?, location: String?): ApiResult<Memo> = api.put("/memos/$id",
        json.encodeToString(UpdateMemoDto(body, shared?.let { if (it) "shared" else "private" }, category, location))) {
        json.decodeFromString<MemoDto>(it).toDomain()
    }
    override suspend fun delete(id: Int): ApiResult<Unit> = api.delete("/memos/$id") { }
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
        fun memos(body: String): List<Memo> = json.decodeFromString<MemoListDto>(body).memos.map { it.toDomain() }
    }
}
