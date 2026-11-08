package com.loadmate.app.feature.settings.domain

/** 백엔드 연결 상태 (F65 연결 인식 상태 표시). */
sealed interface ServerStatus {
    /** release 빌드에 서버 주소가 주입되지 않음 */
    data object NotConfigured : ServerStatus
    data class Online(val serverVersion: String, val database: DatabaseStatus) : ServerStatus
    data class Offline(val reason: String) : ServerStatus
}

enum class DatabaseStatus { OK, UNAVAILABLE, UNKNOWN }

interface ConnectionRepository {
    suspend fun checkServer(): ServerStatus
}
