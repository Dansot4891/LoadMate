package com.loadmate.app.feature.records.presentation.memo

import com.loadmate.app.feature.records.domain.Memo

/** 메모가 붙는 대상 (F52): 차량 · 장소 · 이번 운행. "전체"는 이 운행과 관련된 대상을 모아 보는 선택지다 */
data class MemoScope(val type: String, val id: String, val label: String) {
    val isVehicle: Boolean get() = type == "vehicle"
    val isAll: Boolean get() = type == ALL_TYPE

    companion object {
        private const val ALL_TYPE = "all"
        val ALL = MemoScope(ALL_TYPE, "", "전체")
    }
}

/** "차량" 메모를 거르는 기준 */
enum class NoteFilter(val label: String) { ALL("전체"), TOOL("도구"), ISSUE("차량 문제"), OTHER("기타") }

/** 작성 화면의 종류 선택. category 가 null 이면 서버(AI)가 나눈다 */
enum class NoteKind(val label: String, val category: String?) { AUTO("자동", null), TOOL("도구", "tool"), ISSUE("차량 문제", "issue"), OTHER("기타", "other") }

/** vehicle 이면 위치·종류를, 아니면 이번 운행 표시 여부를 입력받는다 */
data class MemoDraft(
    val body: String = "",
    val location: String = "",
    val kind: NoteKind = NoteKind.AUTO,
    val thisTripOnly: Boolean = false,
    val editingId: Int? = null,
    val vehicle: Boolean = false,
    /** 새 메모를 저장할 대상. 수정할 때는 쓰지 않는다 */
    val target: MemoScope? = null,
)

data class MemoUiState(
    val scopes: List<MemoScope> = emptyList(),
    val scope: MemoScope? = null,
    val filter: NoteFilter = NoteFilter.ALL,
    val query: String = "",
    /** 목록이 검색 결과인지. 입력 중인 query 와 구분한다 */
    val searching: Boolean = false,
    val memos: List<Memo> = emptyList(),
    val draft: MemoDraft = MemoDraft(),
    val editorOpen: Boolean = false,
    val deleteTarget: Memo? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
) {
    /** 종류 드롭다운은 "차량" 목록에서만 쓴다 */
    val showsNoteFilter: Boolean get() = scope?.isVehicle == true && !searching
    val shown: List<Memo> get() = if (showsNoteFilter) visibleNotes(memos, filter) else memos
    /** "전체"에서 추가할 때는 저장할 곳을 골라야 한다 */
    val saveTargets: List<MemoScope> get() = if (scope?.isAll == true) scopes.filterNot { it.isAll } else emptyList()

    /** 여러 대상이 섞인 목록에서 카드에 붙일 대상 이름 */
    fun whereLabel(m: Memo): String? {
        if (scope?.isAll != true && !searching) return null
        return scopes.firstOrNull { it.type == m.scopeType && it.id == m.scopeId }?.label ?: when (m.scopeType) {
            "vehicle" -> "다른 차량"
            "place" -> "다른 장소"
            "trip" -> "다른 운행"
            else -> "기타"
        }
    }
}

internal fun visibleNotes(notes: List<Memo>, filter: NoteFilter): List<Memo> = when (filter) {
    NoteFilter.ALL -> notes
    NoteFilter.TOOL -> notes.filter { it.category == "tool" }
    NoteFilter.ISSUE -> notes.filter { it.category == "issue" }
    NoteFilter.OTHER -> notes.filter { it.category !in setOf("tool", "issue") }
}

internal fun categoryLabel(category: String): String = when (category) {
    "tool" -> "도구"
    "issue" -> "차량 문제"
    else -> "기타"
}

internal fun kindOf(category: String): NoteKind = NoteKind.entries.firstOrNull { it.category == category } ?: NoteKind.OTHER

/** 작성 팝업의 안내 한 줄. 키보드가 올라와도 저장 버튼이 보이도록 줄이지 않는다 */
internal fun editorHint(draft: MemoDraft, scopeLabel: String?): String {
    val new = draft.editingId == null
    return listOfNotNull(
        scopeLabel?.takeIf { new }?.let { "${it}에 저장" },
        "자동은 AI가 분류".takeIf { new && draft.vehicle },
        "모든 사용자에게 표시",
    ).joinToString(" · ")
}
