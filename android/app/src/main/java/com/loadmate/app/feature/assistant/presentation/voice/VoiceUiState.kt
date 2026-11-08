package com.loadmate.app.feature.assistant.presentation.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.loadmate.app.AppContainer
import com.loadmate.app.core.pleos.SpeechConnection
import com.loadmate.app.core.pleos.SpeechGateway
import com.loadmate.app.core.pleos.SpeechOutput
import com.loadmate.app.feature.assistant.domain.AssistantAction
import com.loadmate.app.feature.assistant.domain.AssistantPayload
import com.loadmate.app.feature.assistant.domain.AssistantReply
import com.loadmate.app.feature.assistant.domain.AssistantRepository
import com.loadmate.app.feature.records.domain.MemoRepository
import com.loadmate.app.feature.records.domain.NewMemo
import com.loadmate.app.core.network.ApiResult
import com.loadmate.app.feature.trip.domain.TripRepository
import com.loadmate.app.core.network.describe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** 음성 대화 상태: 대기·청취·처리·응답·확인 대기·실패 (기획서 11장) */

enum class VoicePhase { IDLE, LISTENING, PROCESSING, ANSWERED, CONFIRMING, CLARIFYING, FAILED }

data class VoiceUiState(
    val alwaysListening: Boolean = false,
    val open: Boolean = false,
    val phase: VoicePhase = VoicePhase.IDLE,
    val sttReady: Boolean = false,
    val sttError: String? = null,
    val partial: String? = null,
    val heard: String? = null,
    val reply: AssistantReply? = null,
    val typed: String = "",
    val busy: Boolean = false,
)

