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

    private val updates = mutableListOf<List<Any?>>()
    private val memos = object : MemoRepository {
        override suspend fun list(scopeType: String?, scopeId: String?, tripId: String?, query: String?) = ApiResult.Success(emptyList<Memo>())
        override suspend fun forStop(tripId: String, stopId: String) = ApiResult.Success(emptyList<Memo>())
        override suspend fun create(key: String, memo: NewMemo): ApiResult<Memo> = error("not used")
        override suspend fun update(id: Int, body: String?, shared: Boolean?, category: String?, location: String?): ApiResult<Memo> {
            updates += listOf(id, body, location)
            return ApiResult.Success(Memo(id, "vehicle", "v", body ?: "", emptyList(), true, false, "voice", "shared", "t", "shared"))
        }
        override suspend fun delete(id: Int): ApiResult<Unit> {
            deletes += id
            return ApiResult.Success(Unit)
        }
    }
    private val deletes = mutableListOf<Int>()

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
    fun `상시 듣기에서 못 알아들은 말은 대꾸하지 않고 바로 다시 듣는다`() {
        val unknown = """{"kind":"unknown","speech":"잘 알아듣지 못했어요.","display":"잘 알아듣지 못했어요.","intent":"unknown","method":"rule_based"}"""
        val stt = FakeStt(); val tts = FakeTts()
        val a = FakeAssistant(ArrayDeque(listOf(unknown)))
        val v = vm(stt, tts, a)
        v.onAction(VoiceUiAction.ToggleAlwaysListening)
        stt.say("웅성웅성")
        assertEquals(VoicePhase.LISTENING, v.state.value.phase)
        assertTrue(tts.spoken.isEmpty())
        assertEquals(false, v.state.value.open)
    }

    @Test
    fun `도착했다는 말은 확인 창 없이 적재 화면 요청만 보낸다`() {
        val arrived = """{"kind":"answer","speech":"A 도착 확인했습니다.","display":"A 도착 확인했습니다.","action":{"type":"arrived","payload":{"stop_id":"stop-a"},"summary":"s"},"intent":"arrived","method":"rule_based"}"""
        val stt = FakeStt(); val tts = FakeTts()
        val v = vm(stt, tts, FakeAssistant(ArrayDeque(listOf(arrived))))
        val effects = mutableListOf<VoiceEffect>()
        CoroutineScope(UnconfinedTestDispatcher()).launch { v.effect.collect { effects += it } }
        v.onAction(VoiceUiAction.Open)
        stt.say("도착했어")
        assertEquals(listOf<VoiceEffect>(VoiceEffect.Arrived), effects)
        assertEquals(VoicePhase.IDLE, v.state.value.phase)
        assertEquals(false, v.state.value.open)
        assertNull(v.state.value.reply)
        assertEquals("A 도착 확인했습니다.", tts.spoken.last())
        assertTrue(pickupCalls.isEmpty())
    }

    @Test
    fun `AI 수정과 삭제 요청도 확인한 뒤에만 메모를 바꾼다`() {
        fun confirm(type: String, payload: String) = """{"kind":"confirm","speech":"바꿀까요?","display":"바꿀까요?","action":{"type":"$type","payload":$payload,"summary":"s"},"intent":"note","method":"ai","ai_status":"ok"}"""
        val stt = FakeStt(); val tts = FakeTts()
        val v = vm(stt, tts, FakeAssistant(ArrayDeque(listOf(confirm("update_memo", """{"memo_id":7,"location":"조수석 뒤"}"""), confirm("delete_memo", """{"memo_id":8}""")))))
        v.onAction(VoiceUiAction.Open)
        stt.say("핸드카트 조수석 뒤로 옮겼어")
        assertTrue(updates.isEmpty())
        v.onAction(VoiceUiAction.Confirm)
        assertEquals(listOf(7, null, "조수석 뒤"), updates[0])
        v.onAction(VoiceUiAction.Listen)
        stt.say("끈 새로 샀어")
        assertTrue(deletes.isEmpty())
        v.onAction(VoiceUiAction.Confirm)
        assertEquals(listOf(8), deletes)
        assertEquals("삭제했습니다.", tts.spoken.last())
    }

    private val ignoreJson = """{"kind":"ignore","speech":"","display":"","action":null,"intent":"ignore","method":"ai","ai_status":"ok"}"""

    @Test
    fun `상시 듣기 중 소음은 말하지 않고 팝업 없이 다시 듣는다`() {
        val stt = FakeStt(); val tts = FakeTts()
        val v = vm(stt, tts, FakeAssistant(ArrayDeque(listOf(ignoreJson))))
        v.onAction(VoiceUiAction.ToggleAlwaysListening)
        stt.say("아 오늘 진짜 덥네")
        assertTrue(tts.spoken.isEmpty())
        assertEquals(false, v.state.value.open)
        assertEquals(VoicePhase.LISTENING, v.state.value.phase)
        assertTrue(stt.state.value.listening)
    }

    @Test
    fun `상시 듣기 중 업무 답변은 팝업을 연다`() {
        val stt = FakeStt(); val tts = FakeTts()
        val answer = """{"kind":"answer","speech":"박스 3개입니다.","display":"박스 3개입니다.","action":null,"intent":"x","method":"ai","ai_status":"ok"}"""
        val v = vm(stt, tts, FakeAssistant(ArrayDeque(listOf(answer))))
        v.onAction(VoiceUiAction.ToggleAlwaysListening)
        stt.say("뭐 내려")
        assertEquals(true, v.state.value.open)
        assertEquals("박스 3개입니다.", tts.spoken.last())
    }

    @Test
    fun `직접 연 패널에서 소음이면 들은 말을 남기고 대기한다`() {
        val stt = FakeStt(); val tts = FakeTts()
        val v = vm(stt, tts, FakeAssistant(ArrayDeque(listOf(ignoreJson))))
        v.onAction(VoiceUiAction.Open)
        stt.say("음")
        assertEquals(VoicePhase.IDLE, v.state.value.phase)
        assertEquals("음", v.state.value.heard)
        assertNull(v.state.value.reply)
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
