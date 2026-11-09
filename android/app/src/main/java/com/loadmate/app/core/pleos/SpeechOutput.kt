package com.loadmate.app.core.pleos

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.Locale

/** 짧은 음성 안내 (F32). 구현: [PleosSpeechOutput] (TextToSpeech 3.3.1 ON_DEVICE). */
interface SpeechOutput {
    /** null = 연결 전, true = 사용 가능, false = 사용 불가(화면 표시만) */
    val available: StateFlow<Boolean?>
    /** 음성 출력 중에는 마이크 재청취를 보류한다. */
    val speaking: StateFlow<Boolean>
    fun speak(text: String)
    fun release()
}

/**
 * 실측(2026-11-08): ON_DEVICE 는 자격 증명 없이 재생, 내비 전면 상태에서도 재생.
 * speak(text, isFinal=false) 이면 문장 미완료로 처리되어 onDone 이 오지 않음 → 항상 true.
 * 권한 `TTS_SERVICE`. 콘솔 "준비 중" SDK 이며 실차 동작은 미확인.
 * 내비 음성과 겹치지 않도록 [NaviAwareSpeechQueue] 를 거쳐 재생한다.
 */
class PleosSpeechOutput(context: Context) : SpeechOutput {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private val _available = MutableStateFlow<Boolean?>(null)
    override val available: StateFlow<Boolean?> = _available.asStateFlow()
    private val playing = MutableStateFlow(false)
    private val naviVoice = NaviVoiceMonitor(appContext)
    private val queue = NaviAwareSpeechQueue(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        naviSpeaking = naviVoice.speaking,
        playing = playing,
        awaitReady = { _available.first { it != null } == true },
        start = { text -> checkNotNull(tts).speak(text, true) },
        stop = { tts?.stop() },
    )
    override val speaking: StateFlow<Boolean> = queue.busy

    private fun ensure() {
        if (tts != null) return
        naviVoice.start()
        _available.value = null
        try {
            val t = TextToSpeech(appContext, Mode.ON_DEVICE)
            tts = t
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    runCatching { t.setLocale(Locale.KOREA) }
                    t.addEventListener(object : EventListener {
                        override fun onStart() { playing.value = true }
                        override fun onReady() = Unit
                        override fun onDone() { playing.value = false }
                        override fun onStop() { playing.value = false }
                        override fun onUpdatedRms(rms: Double) = Unit
                        override fun onError(type: ErrorType) { playing.value = false; Log.w(TAG, "tts error $type") }
                    })
                    _available.value = true
                }
                override fun onFailure(type: ErrorType) {
                    Log.w(TAG, "tts init fail $type")
                    tts = null
                    _available.value = false
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "tts init exception $t")
            tts = null
            _available.value = false
        }
    }

    override fun speak(text: String) {
        ensure()
        splitSentences(text).forEach(queue::enqueue)
    }

    override fun release() {
        runCatching { tts?.stop() }
        runCatching { tts?.release() }
        tts = null
        naviVoice.stop()
        playing.value = false
    }

    private companion object {
        const val TAG = "LoadmateTts"
    }
}
