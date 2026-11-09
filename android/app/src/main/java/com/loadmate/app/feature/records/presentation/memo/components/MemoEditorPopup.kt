package com.loadmate.app.feature.records.presentation.memo.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.loadmate.app.feature.records.presentation.memo.MemoDraft
import com.loadmate.app.feature.records.presentation.memo.MemoScope
import com.loadmate.app.feature.records.presentation.memo.NoteKind
import com.loadmate.app.feature.records.presentation.memo.editorHint
import com.loadmate.app.shared.designsystem.components.Field
import com.loadmate.app.shared.designsystem.components.PleosButtonStyle
import com.loadmate.app.shared.designsystem.components.PleosDropdown
import com.loadmate.app.shared.designsystem.components.PleosDropdownSize
import com.loadmate.app.shared.designsystem.components.PleosPopupAction
import com.loadmate.app.shared.designsystem.components.PleosSegmentedControl
import com.loadmate.app.shared.designsystem.components.PleosSwitch
import com.loadmate.app.shared.designsystem.components.PleosSystemPopup
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.theme.Pleos

/** targets 가 있으면("전체"에서 추가) 저장할 곳을 고른다. 키보드가 올라와도 저장 버튼이 보이도록 옆 칸과 한 줄에 둔다 */
@Composable
internal fun MemoEditorPopup(
    draft: MemoDraft,
    targets: List<MemoScope>,
    busy: Boolean,
    onDraft: (MemoDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Pleos.colors
    val t = Pleos.type
    val editing = draft.editingId != null
    val choices = if (editing) emptyList() else targets
    PleosSystemPopup(
        title = if (editing) "메모 수정" else "메모 작성",
        onDismiss = onDismiss,
        actions = listOf(
            PleosPopupAction("저장", enabled = !busy && draft.body.isNotBlank(), onClick = onSave),
            PleosPopupAction("취소", PleosButtonStyle.Basic, onClick = onCancel),
        ),
        extended = true,
    ) {
        Field(draft.body, "내용을 입력하세요", Modifier.fillMaxWidth()) { onDraft(draft.copy(body = it)) }
        if (draft.vehicle) {
            val kinds = if (editing) NoteKind.entries.filter { it != NoteKind.AUTO } else NoteKind.entries
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
                TargetDropdown(choices, draft, onDraft, Modifier.weight(1f))
                Field(draft.location, "위치 (선택)", Modifier.weight(1f), singleLine = true) { onDraft(draft.copy(location = it)) }
            }
            PleosSegmentedControl(kinds, draft.kind, { it.label }) { onDraft(draft.copy(kind = it)) }
        } else if (!editing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
                TargetDropdown(choices, draft, onDraft, Modifier.weight(1f))
                BasicText("이번 운행에만 표시", style = t.bodyLarge.copy(color = c.textPrimary))
                PleosSwitch(draft.thisTripOnly, { onDraft(draft.copy(thisTripOnly = it)) })
            }
        }
        BasicText(editorHint(draft, draft.target?.label.takeIf { choices.isEmpty() }), style = t.labelMedium.copy(color = c.textTertiary))
    }
}

@Composable
private fun TargetDropdown(choices: List<MemoScope>, draft: MemoDraft, onDraft: (MemoDraft) -> Unit, modifier: Modifier) {
    if (choices.isEmpty()) return
    PleosDropdown(
        options = choices,
        selected = draft.target,
        label = { it.label },
        placeholder = "저장할 곳",
        modifier = modifier,
        size = PleosDropdownSize.Small,
    ) { onDraft(draft.copy(target = it, vehicle = it.isVehicle)) }
}
