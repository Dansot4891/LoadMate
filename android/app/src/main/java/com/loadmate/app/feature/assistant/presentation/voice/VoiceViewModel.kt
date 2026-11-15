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
import com.loadmate.app.feature.assistant.domain.PendingKind
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
import kotlinx.coroutines.flow.first
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
    private var rearm: kotlinx.coroutines.Job? = null
    private var expiry: kotlinx.coroutines.Job? = null

    init {
        stt.connect()
        // 앱이 말하는 동안(뒷문 경고 등 다른 화면의 안내 포함)은 마이크를 닫는다.
        // 차량 마이크로 자기 음성이 다시 들어가 기사 말로 처리되는 것을 막는다 (2026-11-15 실측)
        tts?.let { output ->
            viewModelScope.launch {
                output.speaking.collect { speaking ->
                    if (speaking && awaitingResult) {
                        stopCurrentListen()
                        local.update { it.copy(phase = restingPhase(it)) }
                        scheduleNextListen()
                    }
                }
            }
        }
        viewModelScope.launch {
            stt.state.collect { sp ->
                if (local.value.alwaysListening && !awaitingResult && sp.connection == SpeechConnection.Ready && local.value.phase == VoicePhase.FAILED && sp.lastError == null) {
                    listen()
                }
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
                        scheduleNextListen()
                    }
                }
            }
        }
    }

    fun onAction(a: VoiceUiAction) {
        when (a) {
            VoiceUiAction.ToggleAlwaysListening -> {
                if (local.value.alwaysListening) {
                    rearm?.cancel(); timeout?.cancel(); stt.stopListening(); awaitingResult = false
                    local.update { it.copy(alwaysListening = false, phase = VoicePhase.IDLE) }
                } else {
                    local.update { it.copy(alwaysListening = true) }
                    listen()
                }
            }
            VoiceUiAction.Open -> { local.update { it.copy(open = true) }; listen() }
            VoiceUiAction.Close -> {
                // 창을 닫으면 질문도 끝난다. 보이지 않는 질문에 이후 말이 대답으로 붙지 않게 한다
                expiry?.cancel(); pending = null; actionKey = null
                local.update { it.copy(open = false, reply = null, phase = if (it.phase == VoicePhase.LISTENING) it.phase else VoicePhase.IDLE) }
                if (!local.value.alwaysListening) { stt.stopListening(); awaitingResult = false; pending = null; local.value = VoiceUiState() }
            }
            VoiceUiAction.Listen -> listen()
            is VoiceUiAction.Typed -> local.update { it.copy(typed = a.text) }
            VoiceUiAction.SubmitTyped -> local.value.typed.takeIf { it.isNotBlank() }?.let { local.update { s -> s.copy(typed = "") }; handle(it) }
            VoiceUiAction.Confirm -> { stopCurrentListen(); confirm() }
            VoiceUiAction.Cancel -> {
                stopCurrentListen()
                expiry?.cancel()
                pending = null
                actionKey = null
                local.update { it.copy(phase = VoicePhase.IDLE, reply = null) }
                tts?.speak("취소했습니다.")
                scheduleNextListen(closePanel = true)
            }
        }
    }

    private fun listen() {
        if (stt.state.value.connection != SpeechConnection.Ready) {
            local.update { it.copy(phase = VoicePhase.FAILED) }
            return
        }
        awaitingResult = true
        rearm?.cancel()
        local.update { it.copy(phase = VoicePhase.LISTENING, heard = null, partial = null) }
        stt.listen()
        // 실측: 준비 전에 말이 끝나면 무음만 들어와 인식이 끝나지 않는다 → 시간 제한 후 다시 말하기 안내
        timeout?.cancel()
        timeout = viewModelScope.launch {
            kotlinx.coroutines.delay(LISTEN_TIMEOUT_MS)
            // 말하는 도중이면 끊지 않고 문장이 끝날 때까지 더 기다린다. 끊으면 들은 말이 통째로 버려진다 (2026-11-15 실측)
            var extra = 0L
            while (awaitingResult && !stt.state.value.partial.isNullOrBlank() && extra < SPEECH_GRACE_MS) {
                kotlinx.coroutines.delay(GRACE_STEP_MS)
                extra += GRACE_STEP_MS
            }
            if (awaitingResult) {
                awaitingResult = false
                val heard = stt.state.value.partial
                stt.stopListening()
                // 더 기다려도 최종 결과가 없으면 지금까지 들은 말로 처리한다
                if (!heard.isNullOrBlank()) { handle(heard); return@launch }
                // 상시 듣기에서는 말이 없을 때가 대부분이므로 창이 열려 있어도 대꾸하지 않고 조용히 다시 듣는다 (2026-11-15)
                if (local.value.alwaysListening) {
                    local.update { it.copy(phase = restingPhase(it)) }
                    scheduleNextListen()
                    return@launch
                }
                local.update { it.copy(phase = VoicePhase.FAILED, heard = "(말을 듣지 못했습니다)") }
                tts?.speak("다시 말해 주세요.")
                scheduleNextListen()
            }
        }
    }

    private fun handle(text: String) {
        // 확인 창이 떠 있으면 그 질문에 대한 대답으로 서버(AI)에 판단을 맡긴다
        val confirming = local.value.reply?.takeIf { it.kind == AssistantReply.Kind.CONFIRM }
        if (confirming != null) {
            handleConfirmAnswer(text, confirming)
            return
        }
        // 소음으로 판단되면 팝업을 띄우지 않도록, 응답을 받은 뒤에 연다
        local.update { it.copy(phase = VoicePhase.PROCESSING, heard = text) }
        viewModelScope.launch {
            when (val r = assistant.interpret(tripId, text, pending, PendingKind.CLARIFY)) {
                is ApiResult.Success -> {
                    val reply = r.value
                    if (reply.kind == AssistantReply.Kind.IGNORE) {
                        // 되묻는 중이면 질문 창을 그대로 두고 대답을 계속 기다린다
                        local.update { if (pending != null) it.copy(phase = VoicePhase.CLARIFYING) else it.copy(phase = VoicePhase.IDLE, reply = null) }
                        if (local.value.alwaysListening) listen()
                        return@launch
                    }
                    // 운전 중 상시 듣기에서는 한 번 못 알아들어도 듣기를 멈추지 않는다. 소음·자기 음성에 대꾸해 말이 반복되는 것도 막는다
                    if (reply.kind == AssistantReply.Kind.UNKNOWN && local.value.alwaysListening && !local.value.open) {
                        local.update { it.copy(phase = VoicePhase.IDLE, reply = null) }
                        listen()
                        return@launch
                    }
                    // 도착은 저장할 것이 없어 확인 창 없이 적재 화면으로 이어 간다.
                    // 도착 안내 음성은 내비가 하므로 겹치지 않게 말하지 않는다 (2026-11-15 사용자 결정)
                    if (reply.actionType == "arrived") {
                        local.update { it.copy(open = false, phase = VoicePhase.IDLE, reply = null) }
                        effects.send(VoiceEffect.Arrived)
                        scheduleNextListen()
                        return@launch
                    }
                    pending =if (reply.kind == AssistantReply.Kind.CLARIFY) reply.actionRaw else null
                    actionKey = if (reply.kind == AssistantReply.Kind.CONFIRM) UUID.randomUUID().toString() else null
                    local.update {
                        it.copy(open = true, reply = reply, phase = when (reply.kind) {
                            AssistantReply.Kind.CONFIRM -> VoicePhase.CONFIRMING
                            AssistantReply.Kind.CLARIFY -> VoicePhase.CLARIFYING
                            else -> VoicePhase.ANSWERED
                        })
                    }
                    tts?.speak(reply.speech)
                    // 답변은 말을 마치면 창을 닫고, 확인·추가 질문은 창을 둔 채 기사의 대답을 듣는다
                    if (reply.kind == AssistantReply.Kind.CONFIRM || reply.kind == AssistantReply.Kind.CLARIFY) expireQuestion(reply) else expiry?.cancel()
                    scheduleNextListen(closePanel = reply.kind == AssistantReply.Kind.ANSWER)
                }
                is ApiResult.Failure -> {
                    local.update { it.copy(open = true, phase = VoicePhase.FAILED, reply = null) }
                    tts?.speak("서버에 연결하지 못했습니다. 화면에서 입력해 주세요.")
                    local.update { it.copy(heard = "$text  (${describe(r.error)})") }
                    scheduleNextListen()
                }
            }
        }
    }

    private fun handleConfirmAnswer(text: String, question: AssistantReply) {
        local.update { it.copy(phase = VoicePhase.PROCESSING, heard = text) }
        viewModelScope.launch {
            val r = assistant.interpret(tripId, text, question.actionRaw, PendingKind.CONFIRM)
            if (local.value.reply !== question) return@launch // 그 사이 버튼으로 확인·취소했다
            val decision = (r as? ApiResult.Success)?.value?.decision
            when (decision) {
                AssistantReply.Decision.YES -> { local.update { it.copy(phase = VoicePhase.CONFIRMING) }; confirm() }
                AssistantReply.Decision.NO -> onAction(VoiceUiAction.Cancel)
                else -> {
                    // 대답인지 알 수 없는 말이나 AI 를 쓸 수 없을 때는 저장하지 않고 창을 둔 채 다시 듣는다
                    local.update { it.copy(phase = VoicePhase.CONFIRMING) }
                    scheduleNextListen()
                }
            }
        }
    }

    /** 되묻기·확인 창에 대답이 없으면 일정 시간 뒤 창을 닫는다. 남겨 두면 이후 아무 소리나 그 대답으로 처리된다 (2026-11-15 실측) */
    private fun expireQuestion(question: AssistantReply) {
        expiry?.cancel()
        expiry = viewModelScope.launch {
            kotlinx.coroutines.delay(QUESTION_TIMEOUT_MS)
            if (local.value.reply === question && !local.value.busy) {
                pending = null
                actionKey = null
                local.update { it.copy(open = false, phase = VoicePhase.IDLE, reply = null, heard = null) }
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
                scheduleNextListen()
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
                        thisTripOnly = p.persistence == "this_trip", tripId = tripId, source = "voice", category = p.category, location = p.location),
                )
                "update_memo" -> memos.update(requireNotNull(p.memoId), p.body, null, p.category, p.location)
                "delete_memo" -> memos.delete(requireNotNull(p.memoId))
                else -> ApiResult.Success(Unit)
            }
            val verb = if (reply.actionType == "delete_memo") "삭제" else "저장"
            when (result) {
                is ApiResult.Success -> {
                    actionKey = null
                    local.update { it.copy(busy = false, phase = VoicePhase.ANSWERED, reply = reply.copy(kind = AssistantReply.Kind.ANSWER, display = "${verb}했습니다 · ${reply.display}", actionType = null)) }
                    tts?.speak("${verb}했습니다.")
                    scheduleNextListen(closePanel = true)
                }
                is ApiResult.Failure -> {
                    // 같은 키로 다시 확인하면 중복 없이 재시도된다
                    local.update { it.copy(busy = false, heard = "$verb 실패 · ${describe(result.error)}") }
                    tts?.speak("${verb}하지 못했습니다.")
                    scheduleNextListen()
                }
            }
        }
    }

    override fun onCleared() {
        rearm?.cancel(); timeout?.cancel(); expiry?.cancel()
        stt.release()
    }

    /** 듣기를 멈췄을 때 돌아갈 상태. 확인·되묻기 창이 떠 있으면 그 상태를 유지한다 */
    private fun restingPhase(s: VoiceUiState) = when (s.reply?.kind) {
        AssistantReply.Kind.CONFIRM -> VoicePhase.CONFIRMING
        AssistantReply.Kind.CLARIFY -> VoicePhase.CLARIFYING
        else -> VoicePhase.IDLE
    }

    private fun stopCurrentListen() {
        if (!awaitingResult) return
        awaitingResult = false
        timeout?.cancel()
        stt.stopListening()
    }

    /** 상시 듣기에서 응답 음성이 끝나면 다시 듣는다. [closePanel] 이면 운전 중 화면을 가리지 않게 창도 닫는다 */
    private fun scheduleNextListen(closePanel: Boolean = false) {
        if (!local.value.alwaysListening) return
        rearm?.cancel()
        rearm = viewModelScope.launch {
            // TTS 시작 콜백이 speak() 호출보다 늦을 수 있으므로 시작과 종료를 순서대로 기다린다.
            val output = tts
            if (output != null && output.available.value != false) {
                val started = kotlinx.coroutines.withTimeoutOrNull(2_000) { output.speaking.first { it } }
                if (started != null) kotlinx.coroutines.withTimeoutOrNull(15_000) { output.speaking.first { !it } }
            }
            if (closePanel) {
                // 말이 끝난 뒤 화면으로도 읽을 수 있게 잠시 둔다
                kotlinx.coroutines.delay(ANSWER_HOLD_MS)
                local.update { it.copy(open = false, phase = VoicePhase.IDLE, reply = null, heard = null) }
            }
            kotlinx.coroutines.delay(400)
            if (local.value.alwaysListening && !awaitingResult && !local.value.busy) listen()
        }
    }

    companion object {
        const val LISTEN_TIMEOUT_MS = 12_000L
        const val SPEECH_GRACE_MS = 10_000L
        const val GRACE_STEP_MS = 250L
        const val ANSWER_HOLD_MS = 3_000L
        const val QUESTION_TIMEOUT_MS = 30_000L

        fun factory(c: AppContainer, tripId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                VoiceViewModel(c.speechGateway, c.speechOutput, c.assistantRepository, c.tripRepository, c.memoRepository, tripId)
            }
        }
    }
}
