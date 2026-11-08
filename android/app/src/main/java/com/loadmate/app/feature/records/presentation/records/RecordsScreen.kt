package com.loadmate.app.feature.records.presentation.records

import com.loadmate.app.shared.designsystem.components.PleosToast
import kotlinx.coroutines.delay
import com.loadmate.app.shared.designsystem.components.PleosLineSegmentedControl
import com.loadmate.app.shared.designsystem.components.PleosSwitch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import com.loadmate.app.shared.designsystem.components.Field
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loadmate.app.shared.designsystem.theme.Pleos
import com.loadmate.app.shared.designsystem.components.PleosButton
import com.loadmate.app.shared.designsystem.components.PleosButtonSize
import com.loadmate.app.shared.designsystem.components.PleosButtonStyle
import com.loadmate.app.shared.designsystem.project.PleosCard
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.project.PleosTag
import com.loadmate.app.shared.format.localTime

@Composable
fun RecordsScreen(vm: RecordsViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.onAction(RecordsUiAction.Refresh) }
    val on = vm::onAction
    val c = Pleos.colors
    val t = Pleos.type
    Box(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxSize().padding(PleosSpacing.xl), horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xl)) {
        // S08 메모
        Column(Modifier.weight(0.55f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PleosSpacing.l)) {
            BasicText("현장 및 개인 메모", style = t.titleMedium.copy(color = c.textPrimary))
            PleosLineSegmentedControl(s.scopes, s.scope.takeIf { s.query.isBlank() }, { it.label }) { on(RecordsUiAction.SelectScope(it)) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
                Field(s.query, "메모 검색", Modifier.weight(1f)) { on(RecordsUiAction.Query(it)) }
                PleosButton("검색", style = PleosButtonStyle.Basic) { on(RecordsUiAction.Search) }
            }
            if (s.memos.isEmpty()) BasicText("메모가 없습니다", style = t.bodyMedium.copy(color = c.textTertiary))
            s.memos.forEach { m ->
                PleosCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.s)) {
                        PleosTag(if (m.shared) "공유" else "개인")
                        if (m.thisTripOnly) PleosTag("이번 운행")
                        if (m.source == "voice") PleosTag("음성")
                        BasicText("작성 ${m.authorId} · 수정 ${m.updatedBy} ${localTime(m.updatedAt)}", Modifier.weight(1f), style = t.labelMedium.copy(color = c.textTertiary))
                        if (m.shared || m.authorId == s.driverId) PleosButton("고치기", style = PleosButtonStyle.Basic, size = PleosButtonSize.Small) { on(RecordsUiAction.Edit(m)) }
                    }
                    BasicText(m.body, style = t.bodyLarge.copy(color = c.textPrimary))
                }
            }
        }
        // 작성 + S07 이력
        Column(Modifier.weight(0.45f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PleosSpacing.l)) {
            PleosCard {
                BasicText(if (s.draft.editingId != null) "메모 고치기" else "메모 쓰기 · ${s.scope?.label ?: "-"}", style = t.titleSmall.copy(color = c.textPrimary))
                Field(s.draft.body, "예: 후문으로 진입, 담당자 오후 2시 이후", Modifier.fillMaxWidth()) { on(RecordsUiAction.Draft(s.draft.copy(body = it))) }
                if (s.draft.editingId == null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xl)) {
                    BasicText("다른 기사와 공유", style = t.bodyLarge.copy(color = c.textPrimary))
                    PleosSwitch(s.draft.shared, { on(RecordsUiAction.Draft(s.draft.copy(shared = it))) })
                    BasicText("이번 운행에만", style = t.bodyLarge.copy(color = c.textPrimary))
                    PleosSwitch(s.draft.thisTripOnly, { on(RecordsUiAction.Draft(s.draft.copy(thisTripOnly = it))) })
                }
                BasicText("개인 메모는 다른 기사에게 보이지 않고 인계에 자동 포함되지 않습니다.", style = t.labelMedium.copy(color = c.textTertiary))
                Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
                    PleosButton("저장", enabled = !s.busy && s.draft.body.isNotBlank() && s.driverId != null) { on(RecordsUiAction.Save) }
                    if (s.draft.editingId != null) PleosButton("취소", style = PleosButtonStyle.Basic) { on(RecordsUiAction.Draft(com.loadmate.app.feature.records.presentation.records.MemoDraft())) }
                }
                if (s.driverId == null) BasicText("상단에서 기사를 먼저 선택하세요", style = t.labelMedium.copy(color = c.negative))
            }
            PleosCard {
                BasicText("작업 수정 이력", style = t.titleSmall.copy(color = c.textPrimary))
                if (s.history.isEmpty()) BasicText("이력이 없습니다", style = t.bodyMedium.copy(color = c.textTertiary))
                s.history.forEach { h ->
                    BasicText(
                        "${localTime(h.createdAt)} · ${if (h.source == "voice") "음성" else "화면"} · ${h.actorId ?: "-"} · ${h.summary}",
                        style = t.labelMedium.copy(color = c.textSecondary),
                    )
                }
            }
        }
    }
    s.message?.let { m ->
        // 일반 안내는 자동으로 닫고, 오류는 기사가 확인할 때까지 남긴다
        LaunchedEffect(m, s.messageIsError) {
            if (!s.messageIsError) {
                delay(4_000L)
                on(RecordsUiAction.DismissMessage)
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            PleosToast(m) { on(RecordsUiAction.DismissMessage) }
        }
    }
    }
}
