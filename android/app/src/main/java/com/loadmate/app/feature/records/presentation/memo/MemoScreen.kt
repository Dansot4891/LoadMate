package com.loadmate.app.feature.records.presentation.memo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loadmate.app.feature.records.presentation.memo.components.MemoCard
import com.loadmate.app.feature.records.presentation.memo.components.MemoEditorPopup
import com.loadmate.app.shared.designsystem.components.Field
import com.loadmate.app.shared.designsystem.components.PleosButton
import com.loadmate.app.shared.designsystem.components.PleosButtonStyle
import com.loadmate.app.shared.designsystem.components.PleosDropdown
import com.loadmate.app.shared.designsystem.components.PleosDropdownSize
import com.loadmate.app.shared.designsystem.components.PleosPopupAction
import com.loadmate.app.shared.designsystem.components.PleosSystemPopup
import com.loadmate.app.shared.designsystem.components.PleosToast
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.theme.Pleos
import kotlinx.coroutines.delay

@Composable
fun MemoScreen(vm: MemoViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.onAction(MemoUiAction.Refresh) }
    val on = vm::onAction
    val c = Pleos.colors
    val t = Pleos.type
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(PleosSpacing.xl).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PleosSpacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
                BasicText("메모", Modifier.weight(1f), style = t.titleMedium.copy(color = c.textPrimary))
                PleosDropdown(
                    options = s.scopes,
                    selected = s.scope.takeIf { !s.searching },
                    label = { it.label },
                    placeholder = "검색 결과",
                    modifier = Modifier.width(320.dp),
                    size = PleosDropdownSize.Small,
                ) { on(MemoUiAction.SelectScope(it)) }
                if (s.showsNoteFilter) PleosDropdown(
                    options = NoteFilter.entries,
                    selected = s.filter,
                    label = { it.label },
                    placeholder = NoteFilter.ALL.label,
                    modifier = Modifier.width(280.dp),
                    size = PleosDropdownSize.Small,
                ) { on(MemoUiAction.SelectFilter(it)) }
                PleosButton("추가", enabled = s.scope != null) { on(MemoUiAction.OpenEditor) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
                Field(s.query, "메모 검색", Modifier.weight(1f)) { on(MemoUiAction.Query(it)) }
                PleosButton("검색", style = PleosButtonStyle.Basic) { on(MemoUiAction.Search) }
            }
            if (s.shown.isEmpty()) BasicText("메모가 없습니다", style = t.bodyMedium.copy(color = c.textTertiary))
            s.shown.forEach { memo ->
                MemoCard(
                    memo, s.whereLabel(memo), s.busy,
                    onEdit = { on(MemoUiAction.Edit(memo)) },
                    onDelete = { on(MemoUiAction.AskDelete(memo)) },
                )
            }
        }
        if (s.editorOpen) {
            MemoEditorPopup(
                s.draft, s.saveTargets, s.busy,
                onDraft = { on(MemoUiAction.Draft(it)) },
                onSave = { on(MemoUiAction.Save) },
                onCancel = { on(MemoUiAction.CancelEdit) },
                onDismiss = { on(MemoUiAction.DismissEditor) },
            )
        }
        s.deleteTarget?.let { target ->
            PleosSystemPopup(
                title = "이 메모를 삭제할까요?",
                onDismiss = { on(MemoUiAction.DismissDelete) },
                actions = listOf(
                    PleosPopupAction("삭제") { on(MemoUiAction.ConfirmDelete) },
                    PleosPopupAction("취소", PleosButtonStyle.Basic) { on(MemoUiAction.DismissDelete) },
                ),
                description = target.body,
            )
        }
        s.message?.let { m ->
            // 일반 안내는 자동으로 닫고, 오류는 기사가 확인할 때까지 남긴다
            LaunchedEffect(m, s.messageIsError) {
                if (!s.messageIsError) {
                    delay(4_000L)
                    on(MemoUiAction.DismissMessage)
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                PleosToast(m) { on(MemoUiAction.DismissMessage) }
            }
        }
    }
}
