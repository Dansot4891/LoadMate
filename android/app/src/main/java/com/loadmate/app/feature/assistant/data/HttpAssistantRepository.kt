package com.loadmate.app.feature.assistant.data

import com.loadmate.app.core.network.ApiClient
import com.loadmate.app.feature.assistant.domain.AssistantReply
import com.loadmate.app.feature.assistant.domain.AssistantRepository
import org.json.JSONObject

class HttpAssistantRepository(private val api: ApiClient) : AssistantRepository {
    override suspend fun interpret(tripId: String, text: String, pending: JSONObject?) =
        api.post("/assistant/interpret", JSONObject().put("trip_id", tripId).put("text", text).put("pending", pending ?: JSONObject.NULL), ::parse)

    companion object {
        fun parse(body: String): AssistantReply {
            val o = JSONObject(body)
            val action = o.optJSONObject("action")
            return AssistantReply(
                kind = when (o.getString("kind")) {
                    "answer" -> AssistantReply.Kind.ANSWER
                    "confirm" -> AssistantReply.Kind.CONFIRM
                    "clarify" -> AssistantReply.Kind.CLARIFY
                    else -> AssistantReply.Kind.UNKNOWN
                },
                speech = o.getString("speech"), display = o.getString("display"),
                actionType = action?.getString("type"), actionPayload = action?.optJSONObject("payload"), actionRaw = action,
                method = o.optString("method", "rule_based"),
            )
        }
    }
}
