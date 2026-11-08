package com.loadmate.app.feature.assistant.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class AssistantReplyDto(val kind: String, val speech: String, val display: String, val action: AssistantActionDto? = null, val method: String = "rule_based")
@Serializable data class AssistantActionDto(val type: String, val payload: AssistantPayloadDto? = null, val summary: String? = null)
@Serializable data class AssistantPayloadDto(
    @SerialName("stop_id") val stopId: String? = null, @SerialName("item_name") val itemName: String? = null,
    val kind: String? = null, val qty: Int? = null, @SerialName("return_place") val returnPlace: String? = null,
    @SerialName("scope_type") val scopeType: String? = null, @SerialName("scope_id") val scopeId: String? = null,
    val body: String? = null, val visibility: String? = null, val persistence: String? = null,
)
