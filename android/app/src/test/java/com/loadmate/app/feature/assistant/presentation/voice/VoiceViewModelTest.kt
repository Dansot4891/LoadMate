package com.loadmate.app.feature.assistant.presentation.voice

import com.loadmate.app.core.pleos.SpeechConnection
import com.loadmate.app.core.pleos.SpeechGateway
import com.loadmate.app.core.pleos.SpeechOutput
import com.loadmate.app.core.pleos.SpeechState
import com.loadmate.app.feature.assistant.data.AssistantRepositoryImpl
import com.loadmate.app.feature.assistant.domain.AssistantReply
import com.loadmate.app.feature.assistant.domain.AssistantRepository
import com.loadmate.app.feature.records.domain.Memo
import com.loadmate.app.feature.records.domain.MemoRepository
import com.loadmate.app.feature.records.domain.NewMemo
import com.loadmate.app.core.network.ApiResult
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.domain.TripRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import com.loadmate.app.feature.assistant.domain.AssistantAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

/** 음성: 확인 전 저장하지 않음(R08), 확인 시 source=voice 로 저장, 재질문은 pending 과 함께 (F40·F41) */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceViewModelTest {
    private class FakeStt : SpeechGateway {
        override val state = MutableStateFlow(SpeechState(connection = SpeechConnection.Ready))
        override fun connect() = Unit
        override fun listen() { state.value = state.value.copy(listening = true, finalText = null, partial = null) }
        override fun stopListening() = Unit
        override fun release() = Unit
        fun say(text: String) { state.value = state.value.copy(listening = false, finalText = text) }
    }

    private class FakeTts : SpeechOutput {
        val spoken = mutableListOf<String>()
        override val available: StateFlow<Boolean?> = MutableStateFlow(true)
        override val speaking: StateFlow<Boolean> = MutableStateFlow(false)
        override fun speak(text: String) { spoken += text }
        override fun release() = Unit
    }

    private class FakeAssistant(val replies: ArrayDeque<String>) : AssistantRepository {
        val calls = mutableListOf<Pair<String, AssistantAction?>>()
        override suspend fun interpret(tripId: String, text: String, pending: AssistantAction?): ApiResult<AssistantReply> {
            calls += text to pending
            return ApiResult.Success(AssistantRepositoryImpl.parse(replies.removeFirst()))
        }
    }

    /** 회수 저장 호출만 기록하는 TripRepository (나머지 호출은 테스트에서 쓰지 않음) */
    private val pickupCalls = mutableListOf<List<Any?>>()
    private val trips: TripRepository = Proxy.newProxyInstance(TripRepository::class.java.classLoader, arrayOf(TripRepository::class.java)) { _, m, args ->
        if (m.name == "addPickup") { pickupCalls += args!!.dropLast(1).toList(); ApiResult.Success(null as Trip?) } else error("unexpected ${m.name}")
    } as TripRepository

    private val memos = object : MemoRepository {
        override suspend fun list(scopeType: String?, scopeId: String?, tripId: String?, query: String?) = ApiResult.Success(emptyList<Memo>())
        override suspend fun forStop(tripId: String, stopId: String) = ApiResult.Success(emptyList<Memo>())
        override suspend fun create(key: String, memo: NewMemo): ApiResult<Memo> = error("not used")
        override suspend fun update(id: Int, body: String?, shared: Boolean?): ApiResult<Memo> = error("not used")
    }

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val confirmJson = """{"kind":"confirm","speech":"A에서 빈 용기 2개 회수, 창고 반납 예정으로 기록할까요?","display":"A에서 빈 용기 2개 회수, 창고 반납 예정으로 기록할까요?",
        "action":{"type":"add_pickup","payload":{"item_name":"빈 용기","kind":"container","qty":2,"return_place":"창고","stop_id":"stop-a"},"summary":"..."},"intent":"add_pickup","method":"rule_based"}"""
    private val clarifyJson = """{"kind":"clarify","speech":"어디로 반납할까요?","display":"어디로 반납할까요?","missing":["return_place"],
        "action":{"type":"add_pickup","payload":{"item_name":"빈 용기","kind":"container","qty":2},"summary":null},"intent":"add_pickup","method":"rule_based"}"""

    private fun vm(stt: FakeStt, tts: FakeTts, a: FakeAssistant, changed: () -> Unit = {}): VoiceViewModel {
        val v = VoiceViewModel(stt, tts, a, trips, memos, "sample-trip-1")
        CoroutineScope(UnconfinedTestDispatcher()).launch { v.state.collect {} }
        return v
    }

    @Test
    fun `변경 요청은 확인 전에는 저장하지 않고, 확인하면 voice 출처로 한 번 저장`() {
        val stt = FakeStt(); val tts = FakeTts(); var changed = 0
        val v = vm(stt, tts, FakeAssistant(ArrayDeque(listOf(confirmJson))), { changed++ })
        v.onAction(VoiceUiAction.Open)
        stt.say("빈 용기 두 개 회수했어 창고로 반납해")
        assertEquals(VoicePhase.CONFIRMING, v.state.value.phase)
        assertTrue(pickupCalls.isEmpty())
        assertTrue(tts.spoken.last().contains("기록할까요"))
        v.onAction(VoiceUiAction.Confirm)
        assertEquals(1, pickupCalls.size)
        assertEquals(listOf("sample-trip-1"), pickupCalls[0].take(1))
        assertEquals(listOf("stop-a", "빈 용기", "container", 2, "창고", "voice"), pickupCalls[0].drop(2))
        assertEquals(0, changed)
    }

    @Test
    fun `취소하면 저장하지 않는다`() {
        val stt = FakeStt(); val tts = FakeTts()
        val v = vm(stt, tts, FakeAssistant(ArrayDeque(listOf(confirmJson))))
        v.onAction(VoiceUiAction.Open)
        stt.say("빈 용기 두 개 회수했어 창고로 반납해")
        v.onAction(VoiceUiAction.Cancel)
        assertTrue(pickupCalls.isEmpty())
        assertNull(v.state.value.reply)
        assertEquals("취소했습니다.", tts.spoken.last())
    }

    @Test
    fun `재질문 후 답은 이전 action 을 pending 으로 보낸다`() {
        val stt = FakeStt(); val tts = FakeTts()
        val a = FakeAssistant(ArrayDeque(listOf(clarifyJson, confirmJson)))
        val v = vm(stt, tts, a)
        v.onAction(VoiceUiAction.Open)
        stt.say("빈 용기 두 개 회수했어")
        assertEquals(VoicePhase.CLARIFYING, v.state.value.phase)
        assertEquals("어디로 반납할까요?", tts.spoken.last())
        v.onAction(VoiceUiAction.Listen)
        stt.say("창고")
        assertEquals(2, a.calls.size)
        assertNull(a.calls[0].second)
        assertEquals("add_pickup", a.calls[1].second!!.type)
        assertEquals(VoicePhase.CONFIRMING, v.state.value.phase)
    }
}
