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
class VoiceViewModel(
    private val stt: SpeechGateway,
    private val tts: SpeechOutput?,
    private val assistant: AssistantRepository,
    private val trips: TripRepository,
    private val memos: MemoRepository,
    private val tripId: String,
) : ViewModel() {

    private val local = MutableStateFlow(VoiceUiState())
    private val effects = Channel<VoiceEffect>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()

    val state: StateFlow<VoiceUiState> = combine(local, stt.state) { s, sp ->
        s.copy(
            sttReady = sp.connection == SpeechConnection.Ready,
            sttError = (sp.connection as? SpeechConnection.Failed)?.reason ?: sp.lastError,
            partial = if (s.phase == VoicePhase.LISTENING) sp.partial else s.partial,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceUiState())

    private var awaitingResult = false
    private var timeout: kotlinx.coroutines.Job? = null
    private var pending: AssistantAction? = null
    private var actionKey: String? = null

    init {
        stt.connect()
        viewModelScope.launch {
            stt.state.collect { sp ->
                if (!awaitingResult) return@collect
                val final = sp.finalText
                when {
                    final != null && final.isBlank() -> {
                        awaitingResult = false
                        local.update { it.copy(phase = VoicePhase.FAILED, heard = "(인식된 말 없음)") }
                    }
                    final != null -> { awaitingResult = false; timeout?.cancel(); handle(final) }
                    sp.lastError != null && !sp.listening -> {
                        awaitingResult = false
                        local.update { it.copy(phase = VoicePhase.FAILED) }
                    }
                }
            }
        }
    }

    fun onAction(a: VoiceUiAction) {
        when (a) {
            VoiceUiAction.Open -> { local.update { it.copy(open = true) }; listen() }
            VoiceUiAction.Close -> { stt.stopListening(); awaitingResult = false; pending = null; local.value = VoiceUiState() }
            VoiceUiAction.Listen -> listen()
            is VoiceUiAction.Typed -> local.update { it.copy(typed = a.text) }
            VoiceUiAction.SubmitTyped -> local.value.typed.takeIf { it.isNotBlank() }?.let { local.update { s -> s.copy(typed = "") }; handle(it) }
            VoiceUiAction.Confirm -> confirm()
            VoiceUiAction.Cancel -> {
                pending = null
                actionKey = null
                local.update { it.copy(phase = VoicePhase.IDLE, reply = null) }
                tts?.speak("취소했습니다.")
            }
        }
    }

    private fun listen() {
        if (!state.value.sttReady) {
            local.update { it.copy(phase = VoicePhase.FAILED) }
            return
        }
        awaitingResult = true
        local.update { it.copy(phase = VoicePhase.LISTENING, heard = null, partial = null) }
        stt.listen()
        // 실측: 준비 전에 말이 끝나면 무음만 들어와 인식이 끝나지 않는다 → 시간 제한 후 다시 말하기 안내
        timeout?.cancel()
        timeout = viewModelScope.launch {
            kotlinx.coroutines.delay(LISTEN_TIMEOUT_MS)
            if (awaitingResult) {
                awaitingResult = false
                stt.stopListening()
                local.update { it.copy(phase = VoicePhase.FAILED, heard = "(말을 듣지 못했습니다)") }
                tts?.speak("다시 말해 주세요.")
            }
        }
    }

    private fun handle(text: String) {
        local.update { it.copy(phase = VoicePhase.PROCESSING, heard = text) }
        viewModelScope.launch {
            when (val r = assistant.interpret(tripId, text, pending)) {
                is ApiResult.Success -> {
                    val reply = r.value
                    pending = if (reply.kind == AssistantReply.Kind.CLARIFY) reply.actionRaw else null
                    actionKey = if (reply.kind == AssistantReply.Kind.CONFIRM) UUID.randomUUID().toString() else null
                    local.update {
                        it.copy(reply = reply, phase = when (reply.kind) {
                            AssistantReply.Kind.CONFIRM -> VoicePhase.CONFIRMING
                            AssistantReply.Kind.CLARIFY -> VoicePhase.CLARIFYING
                            else -> VoicePhase.ANSWERED
                        })
                    }
                    tts?.speak(reply.speech)
                }
                is ApiResult.Failure -> {
                    local.update { it.copy(phase = VoicePhase.FAILED, reply = null) }
                    tts?.speak("서버에 연결하지 못했습니다. 화면에서 입력해 주세요.")
                    local.update { it.copy(heard = "$text  (${describe(r.error)})") }
                }
            }
        }
    }

    private fun confirm() {
        val reply = local.value.reply ?: return
        val p = reply.actionPayload ?: AssistantPayload()
        val key = actionKey ?: UUID.randomUUID().toString().also { actionKey = it }
        if (local.value.busy) return
        when (reply.actionType) {
            "navigate_next" -> {
                viewModelScope.launch { effects.send(VoiceEffect.AskRoute) }
                local.update { it.copy(phase = VoicePhase.IDLE, reply = null, open = false) }
                return
            }
        }
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result: ApiResult<*> = when (reply.actionType) {
                "add_pickup" -> trips.addPickup(
                    tripId, key, p.stopId, requireNotNull(p.itemName), requireNotNull(p.kind),
                    requireNotNull(p.qty), requireNotNull(p.returnPlace), "voice",
                )
                "add_memo" -> memos.create(
                    key,
                    NewMemo(requireNotNull(p.scopeType), requireNotNull(p.scopeId), requireNotNull(p.body), shared = p.visibility == "shared",
                        thisTripOnly = p.persistence == "this_trip", tripId = tripId, source = "voice"),
                )
                else -> ApiResult.Success(Unit)
            }
            when (result) {
                is ApiResult.Success -> {
                    actionKey = null
                    local.update { it.copy(busy = false, phase = VoicePhase.ANSWERED, reply = reply.copy(kind = AssistantReply.Kind.ANSWER, display = "저장했습니다 · ${reply.display}", actionType = null)) }
                    tts?.speak("저장했습니다.")
                }
                is ApiResult.Failure -> {
                    // 같은 키로 다시 확인하면 중복 없이 재시도된다
                    local.update { it.copy(busy = false, heard = "저장 실패 · ${describe(result.error)}") }
                    tts?.speak("저장하지 못했습니다.")
                }
            }
        }
    }

    override fun onCleared() {
        stt.release()
    }

    companion object {
        const val LISTEN_TIMEOUT_MS = 12_000L

        fun factory(c: AppContainer, tripId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                VoiceViewModel(c.speechGateway, c.speechOutput, c.assistantRepository, c.tripRepository, c.memoRepository, tripId)
            }
        }
    }
}
