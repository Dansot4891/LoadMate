package com.loadmate.app.feature.records.presentation.memo

import com.loadmate.app.feature.records.domain.Memo

sealed interface MemoUiAction {
    data object Refresh : MemoUiAction
    data class SelectScope(val scope: MemoScope) : MemoUiAction
    data class SelectFilter(val filter: NoteFilter) : MemoUiAction
    data class Query(val text: String) : MemoUiAction
    data object Search : MemoUiAction
    data object OpenEditor : MemoUiAction
    data class Draft(val draft: MemoDraft) : MemoUiAction
    data object Save : MemoUiAction
    /** 취소 버튼: 쓰던 내용을 버리고 닫는다 */
    data object CancelEdit : MemoUiAction
    /** 닫기·바깥 누름: 새 메모의 쓰던 내용은 남긴다 */
    data object DismissEditor : MemoUiAction
    data class Edit(val memo: Memo) : MemoUiAction
    data class AskDelete(val memo: Memo) : MemoUiAction
    data object DismissDelete : MemoUiAction
    data object ConfirmDelete : MemoUiAction
    data object DismissMessage : MemoUiAction
}
