package com.loadmate.app.feature.assistant.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.loadmate.app.core.designsystem.Pleos
import com.loadmate.app.core.designsystem.PleosButton
import com.loadmate.app.core.designsystem.PleosButtonStyle
import com.loadmate.app.core.designsystem.PleosShape
import com.loadmate.app.core.designsystem.PleosSpacing
import com.loadmate.app.core.designsystem.PleosTag
import com.loadmate.app.feature.assistant.domain.AssistantReply

@Composable
fun VoicePanel(s: VoiceUiState, on: (VoiceUiAction) -> Unit) {
    if (!s.open) return
    val c = Pleos.colors
    val t = Pleos.type
    Box(Modifier.fillMaxSize().background(c.overlayDefault).clickable { on(VoiceUiAction.Close) }, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.widthIn(max = 1100.dp).fillMaxWidth().clickable(enabled = false) {}
                .background(c.surfaceBasic, RoundedCornerShape(PleosShape.large)).padding(PleosSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(PleosSpacing.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
                BasicText(
                    when (s.phase) {
                        VoicePhase.IDLE -> "말씀하세요"
                        VoicePhase.LISTENING -> "듣는 중…"
                        VoicePhase.PROCESSING -> "처리 중…"
                        VoicePhase.ANSWERED -> "응답"
                        VoicePhase.CONFIRMING -> "확인 대기"
                        VoicePhase.CLARIFYING -> "추가 질문"
                        VoicePhase.FAILED -> if (s.sttReady) "다시 말하거나 직접 입력하세요" else "음성 사용 불가 · 직접 입력하세요"
                    },
                    Modifier.weight(1f),
                    style = t.titleSmall.copy(color = if (s.phase == VoicePhase.FAILED) c.negative else c.textPrimary),
                )
                PleosTag("음성 인식 ON_DEVICE")
                PleosTag("해석: 규칙 기반")
            }
            (s.partial?.takeIf { s.phase == VoicePhase.LISTENING } ?: s.heard)?.let {
                BasicText("들은 말: \"$it\"", style = t.bodyLarge.copy(color = c.textSecondary))
            }
            s.reply?.let { r ->
                BasicText(r.display, style = t.titleSmall.copy(color = c.textPrimary))
                if (r.kind == AssistantReply.Kind.CONFIRM) {
                    BasicText("들은 말과 내용이 맞으면 확인을 누르세요. 확인 전에는 저장하지 않습니다.", style = t.labelMedium.copy(color = c.textTertiary))
                    Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
                        PleosButton("확인", enabled = !s.busy) { on(VoiceUiAction.Confirm) }
                        PleosButton("취소", style = PleosButtonStyle.Basic) { on(VoiceUiAction.Cancel) }
                        PleosButton("다시 말하기", style = PleosButtonStyle.Basic) { on(VoiceUiAction.Listen) }
                    }
                }
            }
            if (s.phase == VoicePhase.FAILED && s.sttError != null) {
                BasicText("음성 인식 상태: ${s.sttError}", style = t.labelMedium.copy(color = c.textTertiary))
            }
            // F43 직접 입력 대체
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
                Box(
                    Modifier.weight(1f).heightIn(min = PleosShape.minTouch).background(c.fieldBackground, RoundedCornerShape(PleosShape.small))
                        .padding(horizontal = PleosSpacing.l),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (s.typed.isEmpty()) BasicText("직접 입력 (예: 다음 장소에 뭐 내려야 해)", style = t.bodyMedium.copy(color = c.textQuaternary))
                    BasicTextField(
                        s.typed, { on(VoiceUiAction.Typed(it)) }, Modifier.fillMaxWidth(),
                        textStyle = t.bodyMedium.copy(color = c.textPrimary), singleLine = true, cursorBrush = SolidColor(c.textPrimary),
                    )
                }
                PleosButton("보내기", style = PleosButtonStyle.Basic, enabled = s.typed.isNotBlank()) { on(VoiceUiAction.SubmitTyped) }
                PleosButton(if (s.phase == VoicePhase.LISTENING) "듣는 중" else "다시 듣기", enabled = s.sttReady && s.phase != VoicePhase.LISTENING) {
                    on(VoiceUiAction.Listen)
                }
                PleosButton("닫기", style = PleosButtonStyle.Basic) { on(VoiceUiAction.Close) }
            }
        }
    }
}
