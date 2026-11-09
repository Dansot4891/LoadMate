package com.loadmate.app.feature.records.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class MemoListDto(val memos: List<MemoDto>)
@Serializable data class MemoEnvelopeDto(val memo: MemoDto)
@Serializable data class MemoDto(
    val id: Int, @SerialName("scope_type") val scopeType: String, @SerialName("scope_id") val scopeId: String, val body: String,
    val tags: List<String> = emptyList(), val visibility: String, val persistence: String, val source: String = "screen",
    @SerialName("author_id") val authorId: String, @SerialName("updated_at") val updatedAt: String, @SerialName("updated_by") val updatedBy: String,
    val category: String = "other", val location: String? = null,
)

@Serializable data class CreateMemoDto(
    @SerialName("idempotency_key") val key: String, @SerialName("scope_type") val scopeType: String,
    @SerialName("scope_id") val scopeId: String, val body: String, val tags: List<String>,
    val visibility: String, val persistence: String, @SerialName("trip_id") val tripId: String?, val source: String,
    val category: String? = null, val location: String? = null,
)
@Serializable data class UpdateMemoDto(val body: String? = null, val visibility: String? = null, val category: String? = null, val location: String? = null)
