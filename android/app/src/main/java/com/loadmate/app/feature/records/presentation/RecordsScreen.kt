package com.loadmate.app.feature.records.presentation

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loadmate.app.core.designsystem.Pleos
import com.loadmate.app.core.designsystem.PleosButton
import com.loadmate.app.core.designsystem.PleosButtonStyle
import com.loadmate.app.core.designsystem.PleosCard
import com.loadmate.app.core.designsystem.PleosShape
import com.loadmate.app.core.designsystem.PleosSpacing
import com.loadmate.app.core.designsystem.PleosTag
import com.loadmate.app.feature.trip.presentation.localTime

@Composable
fun RecordsScreen(vm: RecordsViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.onAction(RecordsUiAction.Refresh) }
    val on = vm::onAction
    val c = Pleos.colors
    val t = Pleos.type
    Row(Modifier.fillMaxSize().padding(PleosSpacing.xl), horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xl)) {
        // S08 메모
        Column(Modifier.weight(0.55f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PleosSpacing.l)) {
            BasicText("현장 및 개인 메모", style = t.titleMedium.copy(color = c.textPrimary))
            Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.s)) {
                s.scopes.forEach { sc ->
                    PleosButton(sc.label, style = if (sc == s.scope && s.query.isBlank()) PleosButtonStyle.Filled else PleosButtonStyle.Basic) {
                        on(RecordsUiAction.SelectScope(sc))
                    }
                }
            }
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
                        if (m.shared || m.authorId == s.driverId) PleosButton("고치기", style = PleosButtonStyle.Basic) { on(RecordsUiAction.Edit(m)) }
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
                if (s.draft.editingId == null) Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.s)) {
                    PleosButton(if (s.draft.shared) "공유" else "개인", style = PleosButtonStyle.Basic) { on(RecordsUiAction.Draft(s.draft.copy(shared = !s.draft.shared))) }
                    PleosButton(if (s.draft.thisTripOnly) "이번 운행만" else "상시", style = PleosButtonStyle.Basic) { on(RecordsUiAction.Draft(s.draft.copy(thisTripOnly = !s.draft.thisTripOnly))) }
                }
                BasicText("개인 메모는 다른 기사에게 보이지 않고 인계에 자동 포함되지 않습니다.", style = t.labelMedium.copy(color = c.textTertiary))
                Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
                    PleosButton("저장", enabled = !s.busy && s.draft.body.isNotBlank() && s.driverId != null) { on(RecordsUiAction.Save) }
                    if (s.draft.editingId != null) PleosButton("취소", style = PleosButtonStyle.Basic) { on(RecordsUiAction.Draft(com.loadmate.app.feature.records.presentation.MemoDraft())) }
                }
                if (s.driverId == null) BasicText("상단에서 기사를 먼저 선택하세요", style = t.labelMedium.copy(color = c.negative))
                s.message?.let { BasicText(it, style = t.labelMedium.copy(color = c.textSecondary)) }
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
}

@Composable
internal fun Field(value: String, hint: String, modifier: Modifier, onChange: (String) -> Unit) {
    val c = Pleos.colors
    val t = Pleos.type
    Box(
        modifier.heightIn(min = PleosShape.minTouch).background(c.fieldBackground, RoundedCornerShape(PleosShape.small)).padding(horizontal = PleosSpacing.l),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) BasicText(hint, style = t.bodyMedium.copy(color = c.textQuaternary))
        BasicTextField(value, onChange, Modifier.fillMaxWidth(), textStyle = t.bodyMedium.copy(color = c.textPrimary), cursorBrush = SolidColor(c.textPrimary))
    }
}
