package com.loadmate.app.feature.assistant.domain

import com.loadmate.app.core.network.ApiResult

/** 서버 음성 해석 결과 (계약: api-contract "음성 해석"). 변경 요청은 확인 전 저장하지 않는다 (R08) */
data class AssistantReply(
    val kind: Kind,
    val speech: String,
    val display: String,
    /** confirm/clarify 일 때 실행할 작업. payload 는 서버 JSON 그대로 */
    val actionType: String?,
    val actionPayload: AssistantPayload?,
    val actionRaw: AssistantAction?,
    val method: String,
) {
    enum class Kind { ANSWER, CONFIRM, CLARIFY, UNKNOWN }
}

data class AssistantAction(val type: String, val payload: AssistantPayload?, val summary: String?)

data class AssistantPayload(
    val stopId: String? = null, val itemName: String? = null, val kind: String? = null, val qty: Int? = null,
    val returnPlace: String? = null, val scopeType: String? = null, val scopeId: String? = null,
    val body: String? = null, val visibility: String? = null, val persistence: String? = null,
)

interface AssistantRepository {
    suspend fun interpret(tripId: String, text: String, pending: AssistantAction?): ApiResult<AssistantReply>
}
