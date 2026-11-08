package com.loadmate.app.core.pleos

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * SpeechToText 3.3.1 ON_DEVICE. 권한 `STT_SERVICE`. 서비스: 에뮬레이터 `ai.pleos.playground.caas` (SPEECH_START).
 * ON_DEVICE 는 registerApp(Client ID/Secret) 없이 사용 (공식 문서). 자격 증명은 다루지 않는다.
 */
class PleosSpeechGateway(context: Context) : SpeechGateway {

    private val appContext = context.applicationContext
    private var stt: SpeechToText? = null

    private val _state = MutableStateFlow(SpeechState())
    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    private val listener = object : ResultListener {
        // onReady 시점부터 마이크가 열린다 (실측: 상태 표시줄 마이크 아이콘) → 듣는 중으로 표시
        override fun onReady() {
            trace("onReady"); _state.update { it.copy(listening = true) }
        }
        // 실측: 발화 중 부분 결과마다 반복 호출됨 → trace 생략
        override fun onStartedRecognition() = _state.update { it.copy(listening = true) }
        override fun onUpdated(text: String, isFinal: Boolean) {
            trace("onUpdated(final=$isFinal)")
            _state.update { if (isFinal) it.copy(finalText = text, partial = null) else it.copy(partial = text) }
        }
        override fun onUpdatedEpdData(epdOn: Long, epdOff: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() {
            trace("onEndedRecognition"); _state.update { it.copy(listening = false) }
        }
        override fun onError(type: ErrorType) {
            trace("onError($type)"); _state.update { it.copy(listening = false, lastError = type.name) }
        }
    }

    override fun connect() {
        if (stt != null) return
        _state.update { it.copy(connection = SpeechConnection.Connecting) }
        try {
            val s = SpeechToText(appContext, Mode.ON_DEVICE)
            stt = s
            s.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    trace("initialize.onSuccess")
                    runCatching { s.setLocale(Locale.KOREA) }.onFailure { trace("setLocale 실패: $it") }
                    s.addListener(listener)
                    _state.update { it.copy(connection = SpeechConnection.Ready) }
                }
                override fun onFailure(type: ErrorType) {
                    trace("initialize.onFailure($type)")
                    stt = null
                    _state.update { it.copy(connection = SpeechConnection.Failed(type.name)) }
                }
            })
        } catch (t: Throwable) {
            stt = null
            trace("initialize 예외: $t")
            _state.update { it.copy(connection = SpeechConnection.Failed(t.toString())) }
        }
    }

    override fun listen() {
        val s = stt
        if (s == null || _state.value.connection != SpeechConnection.Ready) {
            trace("listen 차단: 서비스 미연결"); return
        }
        _state.update { it.copy(partial = null, finalText = null, lastError = null) }
        runCatching { s.request() }.onFailure { trace("request 예외: $it"); _state.update { st -> st.copy(lastError = it.toString()) } }
    }

    override fun stopListening() {
        runCatching { stt?.stop() }
    }

    override fun release() {
        val s = stt ?: return
        runCatching { s.stop() }
        runCatching { s.removeListener(listener) }
        runCatching { s.release() }
        stt = null
        _state.update { it.copy(connection = SpeechConnection.Idle, listening = false) }
    }

    private fun trace(msg: String) {
        Log.i(TAG, msg)
        _state.update { it.copy(trace = (listOf(msg) + it.trace).take(10)) }
    }

    private companion object {
        const val TAG = "LoadmateStt"
    }
}
