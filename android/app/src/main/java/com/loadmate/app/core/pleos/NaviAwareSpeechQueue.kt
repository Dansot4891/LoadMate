package com.loadmate.app.core.pleos

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Loadmate 음성을 내비 음성과 겹치지 않게 한 문장씩 재생한다.
 * - 내비가 말하는 중이면 끝날 때까지 기다린다.
 * - 재생 중 내비가 말을 시작하면 멈추고, 내비가 끝난 뒤 그 문장을 처음부터 다시 말한다.
 * - 내비가 오래 말하거나 계속 끼어들면 안내를 잃지 않도록 한도 뒤에는 겹쳐도 말한다.
 */
internal class NaviAwareSpeechQueue(
    scope: CoroutineScope,
    private val naviSpeaking: StateFlow<Boolean>,
    private val playing: StateFlow<Boolean>,
    private val awaitReady: suspend () -> Boolean,
    private val start: (String) -> Unit,
    private val stop: () -> Unit,
    private val settleMs: Long = 300,
    private val maxNaviWaitMs: Long = 8_000,
    private val startTimeoutMs: Long = 5_000,
    private val maxSpeechMs: Long = 20_000,
    private val maxInterruptions: Int = 2,
) {
    private val texts = Channel<String>(Channel.UNLIMITED)
    private val pending = AtomicInteger(0)
    private val _busy = MutableStateFlow(false)

    /** 대기 중이거나 재생 중이면 true. 이 동안 마이크 재청취를 보류한다. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        scope.launch {
            for (text in texts) {
                if (awaitReady()) playWithoutOverlap(text)
                if (pending.decrementAndGet() == 0) _busy.value = false
            }
        }
    }

    fun enqueue(text: String) {
        pending.incrementAndGet()
        _busy.value = true
        texts.trySend(text)
    }

    private suspend fun playWithoutOverlap(text: String) {
        var interruptions = 0
        while (true) {
            val quiet = waitForNaviQuiet()
            if (!startAndAwaitPlaying(text)) return
            // 기다리다 한도를 넘겨 내비와 겹쳐 시작했다면 끊지 않는다.
            val mayInterrupt = quiet && interruptions < maxInterruptions
            if (!finishedWithoutInterruption(mayInterrupt)) {
                runCatching(stop)
                withTimeoutOrNull(startTimeoutMs) { playing.first { !it } }
                interruptions++
                Log.i(TAG, "interrupted by navi voice ($interruptions)")
                continue
            }
            return
        }
    }

    /** @return 내비가 조용해졌으면 true, 한도를 넘겨 포기했으면 false */
    private suspend fun waitForNaviQuiet(): Boolean = withTimeoutOrNull(maxNaviWaitMs) {
        while (true) {
            naviSpeaking.first { !it }
            // 내비의 효과음과 음성 사이 짧은 틈에 끼어들지 않도록 잠시 더 본다.
            delay(settleMs)
            if (!naviSpeaking.value) break
        }
        true
    } ?: false

    private suspend fun startAndAwaitPlaying(text: String): Boolean {
        if (runCatching { start(text) }.isFailure) return false
        return withTimeoutOrNull(startTimeoutMs) { playing.first { it } } != null
    }

    /** @return 끝까지 재생했으면 true, 내비가 끼어들었으면 false */
    private suspend fun finishedWithoutInterruption(mayInterrupt: Boolean): Boolean {
        val interrupted = withTimeoutOrNull(maxSpeechMs) {
            combine(playing, naviSpeaking) { isPlaying, navi -> if (!isPlaying) false else if (mayInterrupt && navi) true else null }
                .first { it != null }
        }
        return interrupted != true
    }

    private companion object {
        const val TAG = "LoadmateTts"
    }
}
