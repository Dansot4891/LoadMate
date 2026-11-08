package com.loadmate.app.feature.assistant.data.mapper

import com.loadmate.app.feature.assistant.data.dto.*
import com.loadmate.app.feature.assistant.domain.*

internal fun AssistantReplyDto.toDomain() = AssistantReply(
    when (kind) { "answer" -> AssistantReply.Kind.ANSWER; "confirm" -> AssistantReply.Kind.CONFIRM; "clarify" -> AssistantReply.Kind.CLARIFY; else -> AssistantReply.Kind.UNKNOWN },
    speech, display, action?.type, action?.payload?.toDomain(), action?.toDomain(), method,
)
internal fun AssistantActionDto.toDomain() = AssistantAction(type, payload?.toDomain(), summary)
internal fun AssistantPayloadDto.toDomain() = AssistantPayload(stopId, itemName, kind, qty, returnPlace, scopeType, scopeId, body, visibility, persistence)
internal fun AssistantAction.toDto() = AssistantActionDto(type, payload?.toDto(), summary)
internal fun AssistantPayload.toDto() = AssistantPayloadDto(stopId, itemName, kind, qty, returnPlace, scopeType, scopeId, body, visibility, persistence)
