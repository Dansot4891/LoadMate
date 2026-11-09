package com.loadmate.app.feature.records.domain

import com.loadmate.app.core.network.ApiResult

/** 현장·차량·장비·운행 메모 (F49~F54). 개인 메모는 작성자만 본다 (R12) */
data class Memo(
    val id: Int,
    val scopeType: String,
    val scopeId: String,
    val body: String,
    val tags: List<String>,
    val shared: Boolean,
    val thisTripOnly: Boolean,
    val source: String,
    val authorId: String,
    val updatedAt: String,
    val updatedBy: String,
    /** tool(도구) · issue(차량 문제) · place(장소) · other */
    val category: String = "other",
    val location: String? = null,
)

data class NewMemo(
    val scopeType: String,
    val scopeId: String,
    val body: String,
    val shared: Boolean,
    val thisTripOnly: Boolean,
    val tripId: String?,
    val tags: List<String> = emptyList(),
    val source: String = "screen",
    /** null 이면 서버가 분류한다 */
    val category: String? = null,
    val location: String? = null,
)

interface MemoRepository {
    suspend fun list(scopeType: String?, scopeId: String?, tripId: String?, query: String?): ApiResult<List<Memo>>
    suspend fun forStop(tripId: String, stopId: String): ApiResult<List<Memo>>
    suspend fun create(key: String, memo: NewMemo): ApiResult<Memo>
    suspend fun update(id: Int, body: String?, shared: Boolean?, category: String? = null, location: String? = null): ApiResult<Memo>
    suspend fun delete(id: Int): ApiResult<Unit>
}
