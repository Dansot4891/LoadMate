package com.loadmate.app.feature.settings.data

import com.loadmate.app.feature.settings.domain.DatabaseStatus
import com.loadmate.app.feature.settings.domain.ServerStatus
import org.json.JSONException
import org.json.JSONObject

/** GET /health 응답 → 앱 모델. 계약: docs/api-contract.md */
internal object HealthResponseParser {
    fun parse(body: String): ServerStatus = try {
        val json = JSONObject(body)
        val db = when (json.optString("database")) {
            "ok" -> DatabaseStatus.OK
            "unavailable" -> DatabaseStatus.UNAVAILABLE
            else -> DatabaseStatus.UNKNOWN
        }
        ServerStatus.Online(serverVersion = json.optString("version", "?"), database = db)
    } catch (e: JSONException) {
        ServerStatus.Offline("응답 형식 오류")
    }
}
