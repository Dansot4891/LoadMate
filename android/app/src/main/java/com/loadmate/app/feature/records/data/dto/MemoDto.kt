package com.loadmate.app.feature.records.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class MemoListDto(val memos: List<MemoDto>)
@Serializable data class MemoEnvelopeDto(val memo: MemoDto)
@Serializable data class MemoDto(
    val id: Int, @SerialName("scope_type") val scopeType: String, @SerialName("scope_id") val scopeId: String, val body: String,
    val tags: List<String> = emptyList(), val visibility: String, val persistence: String, val source: String = "screen",
    @SerialName("author_id") val authorId: String, @SerialName("updated_at") val updatedAt: String, @SerialName("updated_by") val updatedBy: String,
)
