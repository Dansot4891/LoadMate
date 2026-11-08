package com.loadmate.app.core.pleos

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** 짧은 음성 안내 (F32). 구현: [PleosSpeechOutput] (TextToSpeech 3.3.1 ON_DEVICE). */
interface SpeechOutput {
    /** null = 연결 전, true = 사용 가능, false = 사용 불가(화면 표시만) */
    val available: StateFlow<Boolean?>
    fun speak(text: String)
    fun release()
}

/**
 * 실측(2026-11-08): ON_DEVICE 는 자격 증명 없이 재생, 내비 전면 상태에서도 재생.
 * speak(text, isFinal=false) 이면 문장 미완료로 처리되어 onDone 이 오지 않음 → 항상 true.
 * 권한 `TTS_SERVICE`. 콘솔 "준비 중" SDK 이며 실차 동작은 미확인.
 */
class PleosSpeechOutput(context: Context) : SpeechOutput {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private val queue = ArrayDeque<String>()
    private val _available = MutableStateFlow<Boolean?>(null)
    override val available: StateFlow<Boolean?> = _available.asStateFlow()

    private fun ensure() {
        if (tts != null) return
        try {
            val t = TextToSpeech(appContext, Mode.ON_DEVICE)
            tts = t
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    runCatching { t.setLocale(Locale.KOREA) }
                    t.addEventListener(object : EventListener {
                        override fun onStart() = Unit
                        override fun onReady() = Unit
                        override fun onDone() = Unit
                        override fun onStop() = Unit
                        override fun onUpdatedRms(rms: Double) = Unit
                        override fun onError(type: ErrorType) { Log.w(TAG, "tts error $type") }
                    })
                    ready = true
                    _available.value = true
                    while (queue.isNotEmpty()) t.speak(queue.removeFirst(), true)
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
        val t = tts ?: return
        if (ready) runCatching { t.speak(text, true) } else queue.addLast(text)
    }

    override fun release() {
        runCatching { tts?.stop() }
        runCatching { tts?.release() }
        tts = null
        ready = false
    }

    private companion object {
        const val TAG = "LoadmateTts"
    }
}
