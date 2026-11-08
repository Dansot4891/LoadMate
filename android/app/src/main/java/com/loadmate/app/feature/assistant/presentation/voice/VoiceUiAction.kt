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
import com.loadmate.app.feature.trip.presentation.describe
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

sealed interface VoiceUiAction {
    data object Open : VoiceUiAction
    data object Close : VoiceUiAction
    data object Listen : VoiceUiAction
    data class Typed(val text: String) : VoiceUiAction
    data object SubmitTyped : VoiceUiAction
    data object Confirm : VoiceUiAction
    data object Cancel : VoiceUiAction
}

