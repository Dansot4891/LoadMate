package com.loadmate.app.feature.settings.data

import com.loadmate.app.feature.settings.data.dto.HealthDto
import com.loadmate.app.feature.settings.data.mapper.toDomain
import com.loadmate.app.feature.settings.domain.ServerStatus
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** GET /health 응답 → 앱 모델. 계약: docs/api-contract.md */
internal object HealthResponseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): ServerStatus = try {
        json.decodeFromString<HealthDto>(body).toDomain()
    } catch (e: SerializationException) {
        ServerStatus.Offline("응답 형식 오류")
    } catch (e: IllegalArgumentException) {
        ServerStatus.Offline("응답 형식 오류")
    }
}
