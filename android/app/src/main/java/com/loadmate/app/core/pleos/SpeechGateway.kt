package com.loadmate.app.core.pleos

import kotlinx.coroutines.flow.StateFlow

/**
 * 음성 인식 경계 (F35~F43). 구현: [PleosSpeechGateway] (SpeechToText 3.3.1, ON_DEVICE 모드).
 * 실패 시 화면 입력(F43)으로 대체한다. 데모 문구로 조용히 바꾸지 않는다 (CLAUDE.md 원칙 5).
 */
interface SpeechGateway {
    val state: StateFlow<SpeechState>

    /** 서비스 연결. 이미 연결 중이거나 연결됐으면 무시. */
    fun connect()

    /** 1회 인식 시작. 결과는 [SpeechState.partial] / [SpeechState.finalText] 로 들어온다. */
    fun listen()

    fun stopListening()

    fun release()
}

sealed interface SpeechConnection {
    data object Idle : SpeechConnection
    data object Connecting : SpeechConnection
    data object Ready : SpeechConnection
    data class Failed(val reason: String) : SpeechConnection
}

data class SpeechState(
    val connection: SpeechConnection = SpeechConnection.Idle,
    val listening: Boolean = false,
    val partial: String? = null,
    val finalText: String? = null,
    /** SDK ErrorType 이름 그대로 (예: MIC_ERROR) */
    val lastError: String? = null,
    /** 최근 SDK 콜백 이름 (실측 기록용, 최신이 앞) */
    val trace: List<String> = emptyList(),
)
