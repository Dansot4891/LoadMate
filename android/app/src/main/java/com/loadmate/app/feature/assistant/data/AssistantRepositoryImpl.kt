package com.loadmate.app.feature.assistant.data

import com.loadmate.app.core.network.ApiClient
import com.loadmate.app.feature.assistant.data.dto.AssistantReplyDto
import com.loadmate.app.feature.assistant.data.dto.InterpretRequestDto
import com.loadmate.app.feature.assistant.data.mapper.toDomain
import com.loadmate.app.feature.assistant.data.mapper.toDto
import com.loadmate.app.feature.assistant.domain.AssistantAction
import com.loadmate.app.feature.assistant.domain.AssistantReply
import com.loadmate.app.feature.assistant.domain.AssistantRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AssistantRepositoryImpl(private val api: ApiClient) : AssistantRepository {
    override suspend fun interpret(tripId: String, text: String, pending: AssistantAction?) =
        api.postWaitingForAi("/assistant/interpret", json.encodeToString(InterpretRequestDto(tripId, text, pending?.toDto())), ::parse)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(body: String): AssistantReply = json.decodeFromString<AssistantReplyDto>(body).toDomain()
    }
}
