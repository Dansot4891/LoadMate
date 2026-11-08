package com.loadmate.app.feature.assistant.data

import com.loadmate.app.core.network.ApiClient
import com.loadmate.app.feature.assistant.data.dto.AssistantReplyDto
import com.loadmate.app.feature.assistant.data.mapper.toDomain
import com.loadmate.app.feature.assistant.data.mapper.toDto
import com.loadmate.app.feature.assistant.domain.AssistantAction
import com.loadmate.app.feature.assistant.domain.AssistantReply
import com.loadmate.app.feature.assistant.domain.AssistantRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject

class AssistantRepositoryImpl(private val api: ApiClient) : AssistantRepository {
    override suspend fun interpret(tripId: String, text: String, pending: AssistantAction?) =
        api.post("/assistant/interpret", JSONObject().put("trip_id", tripId).put("text", text)
            .put("pending", pending?.let { JSONObject(json.encodeToString(it.toDto())) } ?: JSONObject.NULL), ::parse)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(body: String): AssistantReply = json.decodeFromString<AssistantReplyDto>(body).toDomain()
    }
}
