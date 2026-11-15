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
    /** ai | rule_based */
    val method: String,
    /** ok | unavailable | not_configured */
    val aiStatus: String = "not_configured",
    /** CONFIRM_ANSWER 일 때 확인 창에 대한 기사의 대답 판단 */
    val decision: Decision? = null,
) {
    enum class Kind { ANSWER, CONFIRM, CLARIFY, UNKNOWN, IGNORE, CONFIRM_ANSWER }
    enum class Decision { YES, NO, UNCLEAR }
}

/** 기사의 말이 어떤 질문에 대한 대답인지. 서버가 그 맥락으로 해석한다 */
enum class PendingKind { CLARIFY, CONFIRM }

data class AssistantAction(val type: String, val payload: AssistantPayload?, val summary: String?)

data class AssistantPayload(
    val stopId: String? = null, val itemName: String? = null, val kind: String? = null, val qty: Int? = null,
    val returnPlace: String? = null, val scopeType: String? = null, val scopeId: String? = null,
    val body: String? = null, val visibility: String? = null, val persistence: String? = null,
    val memoId: Int? = null, val category: String? = null, val location: String? = null,
)

interface AssistantRepository {
    suspend fun interpret(tripId: String, text: String, pending: AssistantAction?, pendingKind: PendingKind = PendingKind.CLARIFY): ApiResult<AssistantReply>
}
