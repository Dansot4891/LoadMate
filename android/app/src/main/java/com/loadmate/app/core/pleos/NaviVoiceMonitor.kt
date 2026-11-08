package com.loadmate.app.core.pleos

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 내비 음성이 지금 재생 중인지 알려 준다.
 *
 * 에뮬레이터 실측(2026-11-09): 내비 음성은 재생 용도가 내비 안내(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE),
 * Loadmate TTS 는 USAGE_MEDIA 라서 용도로 구분된다. 재생 목록에는 멈춘 플레이어도 남아 있고
 * 공개 API에 재생 상태 getter 가 없어 설명 문자열의 상태를 본다. 형식이 달라지면 "말하는 중 아님"이 되어
 * 기다리지 않고 바로 말한다(이 기능이 없을 때와 같은 동작).
 */
class NaviVoiceMonitor(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()
    private var registered = false

    private val callback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) = update(configs)
    }

    fun start() {
        if (registered) return
        runCatching {
            audioManager.registerAudioPlaybackCallback(callback, Handler(Looper.getMainLooper()))
            registered = true
            update(audioManager.activePlaybackConfigurations)
        }.onFailure { Log.w(TAG, "playback callback unavailable: $it") }
    }

    fun stop() {
        if (!registered) return
        runCatching { audioManager.unregisterAudioPlaybackCallback(callback) }
        registered = false
        _speaking.value = false
    }

    private fun update(configs: List<AudioPlaybackConfiguration>) {
        val now = configs.any { isPlayingNaviVoice(it.audioAttributes.usage, it.toString()) }
        if (now != _speaking.value) Log.i(TAG, "naviSpeaking=$now")
        _speaking.value = now
    }

    private companion object {
        const val TAG = "LoadmateNaviVoice"
    }
}

internal fun isPlayingNaviVoice(usage: Int, description: String): Boolean =
    usage == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE && "state:started" in description
