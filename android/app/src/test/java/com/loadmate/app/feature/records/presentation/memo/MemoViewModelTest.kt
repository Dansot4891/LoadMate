package com.loadmate.app.feature.records.presentation.memo

import com.loadmate.app.core.network.ApiError
import com.loadmate.app.core.network.ApiResult
import com.loadmate.app.feature.records.domain.Memo
import com.loadmate.app.feature.records.domain.MemoRepository
import com.loadmate.app.feature.records.domain.NewMemo
import com.loadmate.app.feature.trip.domain.Comparison
import com.loadmate.app.feature.trip.domain.ComparisonState
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.domain.TripRepository
import com.loadmate.app.feature.trip.domain.TripStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class MemoViewModelTest {
    private val trip = Trip("t", "운행", "sample", TripStatus.PLANNED, null, emptyList(), null,
        Comparison(ComparisonState.NO_OBSERVATION, null, null, emptyList(), null, true, 0), vehicleId = "v")
    private val trips = Proxy.newProxyInstance(TripRepository::class.java.classLoader, arrayOf(TripRepository::class.java)) { _, m, _ ->
        if (m.name == "getTrip") ApiResult.Success(trip) else error("unexpected ${m.name}")
    } as TripRepository

    private val vehicleMemo = Memo(1, "vehicle", "v", "핸드카트", emptyList(), true, false, "screen", "shared", "t", "shared", "tool", "뒷문")
    private val tripMemo = Memo(2, "trip", "t", "후문", emptyList(), true, false, "screen", "shared", "t", "shared")
    private var failCreate = false
    private var failList: String? = null
    private val createKeys = mutableListOf<String>()
    private val created = mutableListOf<NewMemo>()
    private val lists = mutableListOf<List<String?>>()
    private val updates = mutableListOf<List<Any?>>()
    private var listed = emptyList<Memo>()
    private val memos = object : MemoRepository {
        override suspend fun list(scopeType: String?, scopeId: String?, tripId: String?, query: String?): ApiResult<List<Memo>> {
            lists += listOf(scopeType, scopeId, tripId, query)
            return if (failList != null && scopeType == failList) ApiResult.Failure(ApiError.Network("ConnectException")) else ApiResult.Success(listed)
        }
        override suspend fun forStop(tripId: String, stopId: String) = ApiResult.Success(emptyList<Memo>())
        override suspend fun create(key: String, memo: NewMemo): ApiResult<Memo> {
            createKeys += key
            created += memo
            return if (failCreate) ApiResult.Failure(ApiError.Network("ConnectException")) else ApiResult.Success(vehicleMemo)
        }
        override suspend fun update(id: Int, body: String?, shared: Boolean?, category: String?, location: String?): ApiResult<Memo> {
            updates += listOf(id, body, category, location)
            return ApiResult.Success(vehicleMemo)
        }
        override suspend fun delete(id: Int): ApiResult<Unit> = error("not used")
    }

    private var keyCount = 0
    private val vm by lazy { MemoViewModel(trips, memos, "t", newKey = { "key-${++keyCount}" }).also { it.onAction(MemoUiAction.Refresh) } }
    private val tripScope get() = vm.state.value.scopes.first { it.type == "trip" }

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun write(body: String) {
        vm.onAction(MemoUiAction.OpenEditor)
        vm.onAction(MemoUiAction.Draft(vm.state.value.draft.copy(body = body)))
    }

    @Test fun `처음에는 전체를 보여 주고 이 운행의 대상만 조회한다`() {
        assertTrue(vm.state.value.scope!!.isAll)
        assertFalse(vm.state.value.showsNoteFilter)
        assertEquals(setOf(listOf("vehicle", "v", null, null), listOf("trip", "t", null, null)), lists.toSet())
    }

    @Test fun `전체는 겹친 메모를 한 번만 최신순으로 보여 준다`() {
        listed = listOf(
            vehicleMemo.copy(id = 1, updatedAt = "2026-11-09T01:00:00Z"),
            vehicleMemo.copy(id = 2, updatedAt = "2026-11-09T02:00:00Z"),
        )
        assertEquals(listOf(2, 1), vm.state.value.shown.map { it.id })
    }

    @Test fun `전체 조회가 하나라도 실패하면 일부만 보여 주지 않고 오류를 알린다`() {
        listed = listOf(vehicleMemo)
        failList = "trip"
        assertEquals(emptyList<Memo>(), vm.state.value.memos)
        assertTrue(vm.state.value.messageIsError)
    }

    @Test fun `전체에서 추가하면 저장할 곳을 고를 수 있고 고른 곳에 저장한다`() {
        assertEquals(listOf("차량", "이번 운행"), vm.state.value.saveTargets.map { it.label })
        write("후문으로 진입")
        assertEquals("차량", vm.state.value.draft.target?.label)
        assertTrue(vm.state.value.draft.vehicle)

        vm.onAction(MemoUiAction.Draft(vm.state.value.draft.copy(target = tripScope, vehicle = false)))
        vm.onAction(MemoUiAction.Save)
        assertEquals("trip", created.single().scopeType)
        assertNull(created.single().category)
    }

    @Test fun `전체와 검색에서는 카드에 대상 이름을 붙이고 다른 대상을 고르면 뺀다`() {
        val other = Memo(9, "place", "elsewhere", "x", emptyList(), true, false, "screen", "shared", "t", "shared")
        assertEquals("차량", vm.state.value.whereLabel(vehicleMemo))
        assertEquals("다른 장소", vm.state.value.whereLabel(other))
        vm.onAction(MemoUiAction.SelectScope(tripScope))
        assertNull(vm.state.value.whereLabel(vehicleMemo))
    }

    @Test fun `차량이 아닌 메모는 종류 필터를 쓰지 않고 분류 없이 저장한다`() {
        vm.onAction(MemoUiAction.SelectScope(tripScope))
        assertFalse(vm.state.value.showsNoteFilter)
        write("후문으로 진입")
        assertFalse(vm.state.value.draft.vehicle)
        vm.onAction(MemoUiAction.Save)
        assertEquals("trip", created.single().scopeType)
        assertNull(created.single().category)
        assertNull(created.single().location)
    }

    @Test fun `차량 메모는 위치와 종류를 저장한다`() {
        vm.onAction(MemoUiAction.SelectScope(vm.state.value.scopes.first { it.isVehicle }))
        assertTrue(vm.state.value.showsNoteFilter)
        assertEquals(listOf("vehicle", "v", null, null), lists.last())
        write("끈 끊어짐")
        vm.onAction(MemoUiAction.Draft(vm.state.value.draft.copy(location = "조수석", kind = NoteKind.ISSUE)))
        vm.onAction(MemoUiAction.Save)
        assertEquals("vehicle", created.single().scopeType)
        assertEquals("issue", created.single().category)
        assertEquals("조수석", created.single().location)
    }

    @Test fun `검색 버튼을 눌러야 검색하고 종류 필터를 무시한다`() {
        vm.onAction(MemoUiAction.SelectScope(vm.state.value.scopes.first { it.isVehicle }))
        vm.onAction(MemoUiAction.SelectFilter(NoteFilter.ISSUE))
        listed = listOf(vehicleMemo)
        vm.onAction(MemoUiAction.Query("핸드"))
        assertFalse(lists.last().contains("핸드"))
        vm.onAction(MemoUiAction.Search)
        assertEquals(listOf(null, null, "t", "핸드"), lists.last())
        assertEquals(1, vm.state.value.shown.size)
        assertFalse(vm.state.value.showsNoteFilter)
    }

    @Test fun `검색 결과를 고칠 때는 메모의 대상에 맞는 값만 보낸다`() {
        vm.onAction(MemoUiAction.Edit(tripMemo))
        assertFalse(vm.state.value.draft.vehicle)
        vm.onAction(MemoUiAction.Save)
        assertEquals(listOf(2, "후문", null, null), updates.last())

        vm.onAction(MemoUiAction.Edit(vehicleMemo))
        vm.onAction(MemoUiAction.Save)
        assertEquals(listOf(1, "핸드카트", "tool", "뒷문"), updates.last())
    }

    @Test fun `바깥을 눌러 닫아도 새 메모 내용은 남고 취소하면 버린다`() {
        write("뒷문 주의")
        vm.onAction(MemoUiAction.DismissEditor)
        assertFalse(vm.state.value.editorOpen)
        assertEquals("뒷문 주의", vm.state.value.draft.body)

        vm.onAction(MemoUiAction.OpenEditor)
        vm.onAction(MemoUiAction.CancelEdit)
        assertFalse(vm.state.value.editorOpen)
        assertEquals("", vm.state.value.draft.body)
    }

    @Test fun `수정 팝업은 어떻게 닫아도 내용이 다음 작성에 남지 않는다`() {
        vm.onAction(MemoUiAction.Edit(vehicleMemo))
        assertTrue(vm.state.value.editorOpen)
        assertEquals(1, vm.state.value.draft.editingId)
        vm.onAction(MemoUiAction.DismissEditor)
        assertEquals(MemoDraft(), vm.state.value.draft)
    }

    @Test fun `저장에 실패하면 팝업과 내용을 유지하고 같은 키로 다시 보낸 뒤 성공하면 닫는다`() {
        failCreate = true
        write("고정 끈 끊어짐")
        vm.onAction(MemoUiAction.Save)
        assertTrue(vm.state.value.editorOpen)
        assertEquals("고정 끈 끊어짐", vm.state.value.draft.body)

        failCreate = false
        vm.onAction(MemoUiAction.Save)
        assertFalse(vm.state.value.editorOpen)
        assertEquals("", vm.state.value.draft.body)
        assertEquals(listOf("key-1", "key-1"), createKeys)
    }

    @Test fun `저장 실패 후 취소하면 다음 메모는 새 키를 쓴다`() {
        failCreate = true
        write("첫 메모")
        vm.onAction(MemoUiAction.Save)
        vm.onAction(MemoUiAction.CancelEdit)
        write("다른 메모")
        vm.onAction(MemoUiAction.Save)
        assertEquals(listOf("key-1", "key-2"), createKeys)
    }

    @Test fun `안내 문구는 저장될 곳과 차량 전용 설명을 상황에 맞게 보여 준다`() {
        assertEquals("차량에 저장 · 자동은 AI가 분류 · 모든 사용자에게 표시", editorHint(MemoDraft(vehicle = true), "차량"))
        assertEquals("이번 운행에 저장 · 모든 사용자에게 표시", editorHint(MemoDraft(), "이번 운행"))
        assertEquals("모든 사용자에게 표시", editorHint(MemoDraft(editingId = 1, vehicle = true), "차량"))
    }
}
