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

sealed interface VoiceEffect {
    /** 다음 장소 안내: 운행 화면의 내비 확인창을 연다 (확인은 거기서 한 번 더) */
    data object AskRoute : VoiceEffect

    /** 기사가 도착했다고 말함: 적재 화면을 연다 (서버 기록 없음) */
    data object Arrived : VoiceEffect
}

/**
 * 조회는 즉시 답하고, 업무 변경은 요약을 보여 주고 확인을 받은 뒤에만 저장한다 (R08·F41).
 * 인식 문장을 화면에 보여 주고 기사가 맞다고 확인해야 실행한다 (2026-11-08 결정).
 */
