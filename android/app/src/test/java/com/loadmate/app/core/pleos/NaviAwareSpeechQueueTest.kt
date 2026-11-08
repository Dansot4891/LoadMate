package com.loadmate.app.core.pleos

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NaviAwareSpeechQueueTest {
    private val navi = MutableStateFlow(false)
    private val playing = MutableStateFlow(false)
    private val started = mutableListOf<String>()
    private var stops = 0

    private fun TestScope.queue(ready: Boolean = true) = NaviAwareSpeechQueue(
        scope = backgroundScope,
        naviSpeaking = navi,
        playing = playing,
        awaitReady = { ready },
        start = { started += it },
        stop = { stops++; playing.value = false },
    )

    @Test fun `내비가 조용하면 바로 말하고 끝나면 busy 해제`() = runTest {
        val q = queue()
        q.enqueue("A")
        advanceTimeBy(301); runCurrent()
        assertEquals(listOf("A"), started)
        assertTrue(q.busy.value)
        playing.value = true; runCurrent()
        playing.value = false; runCurrent()
        assertFalse(q.busy.value)
    }

    @Test fun `내비가 말하는 동안 기다렸다가 끝난 뒤 말한다`() = runTest {
        val q = queue()
        navi.value = true
        q.enqueue("A")
        advanceTimeBy(3_000); runCurrent()
        assertEquals(emptyList<String>(), started)
        assertTrue(q.busy.value)
        navi.value = false
        advanceTimeBy(301); runCurrent()
        assertEquals(listOf("A"), started)
    }

    @Test fun `효과음과 음성 사이 짧은 틈에는 끼어들지 않는다`() = runTest {
        queue()
            .enqueue("A")
        navi.value = true; runCurrent()
        navi.value = false
        advanceTimeBy(100); runCurrent()
        navi.value = true
        advanceTimeBy(1_000); runCurrent()
        assertEquals(emptyList<String>(), started)
    }

    @Test fun `재생 중 내비가 끼어들면 멈추고 끝난 뒤 처음부터 다시 말한다`() = runTest {
        queue().enqueue("A")
        advanceTimeBy(301); runCurrent()
        playing.value = true; runCurrent()
        navi.value = true; runCurrent()
        assertEquals(1, stops)
        assertEquals(listOf("A"), started)
        navi.value = false
        advanceTimeBy(301); runCurrent()
        assertEquals(listOf("A", "A"), started)
    }

    @Test fun `계속 끼어들면 한도 뒤에는 끊지 않는다`() = runTest {
        queue().enqueue("A")
        repeat(2) {
            advanceTimeBy(301); runCurrent()
            playing.value = true; runCurrent()
            navi.value = true; runCurrent()
            navi.value = false
        }
        advanceTimeBy(301); runCurrent()
        playing.value = true; runCurrent()
        navi.value = true; runCurrent()
        assertEquals(2, stops)
        assertEquals(3, started.size)
    }

    @Test fun `내비가 한도를 넘겨 말하면 겹쳐도 말하고 끊지 않는다`() = runTest {
        queue().enqueue("A")
        navi.value = true
        advanceTimeBy(8_001); runCurrent()
        assertEquals(listOf("A"), started)
        playing.value = true; runCurrent()
        assertEquals(0, stops)
    }

    @Test fun `여러 문장은 앞 문장이 끝난 뒤 차례로 말한다`() = runTest {
        val q = queue()
        q.enqueue("A"); q.enqueue("B")
        advanceTimeBy(301); runCurrent()
        playing.value = true; runCurrent()
        assertEquals(listOf("A"), started)
        playing.value = false
        advanceTimeBy(301); runCurrent()
        assertEquals(listOf("A", "B"), started)
    }

    @Test fun `음성 서비스를 쓸 수 없으면 말하지 않고 busy 해제`() = runTest {
        val q = queue(ready = false)
        q.enqueue("A")
        runCurrent()
        assertEquals(emptyList<String>(), started)
        assertFalse(q.busy.value)
    }

    @Test fun `내비 음성 판별은 용도와 재생 상태를 함께 본다`() {
        val nav = android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
        assertTrue(isPlayingNaviVoice(nav, "piid:1 state:started attr:x"))
        assertFalse(isPlayingNaviVoice(nav, "piid:1 state:idle attr:x"))
        assertFalse(isPlayingNaviVoice(nav, "piid:1 state:stopped attr:x"))
        assertFalse(isPlayingNaviVoice(android.media.AudioAttributes.USAGE_MEDIA, "piid:1 state:started attr:x"))
    }
}
