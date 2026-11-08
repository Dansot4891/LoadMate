package com.loadmate.app.feature.assistant.presentation.voice

import com.loadmate.app.shared.designsystem.components.PleosSystemPopupSize
import com.loadmate.app.shared.designsystem.components.PleosPopupAction
import com.loadmate.app.shared.designsystem.components.PleosSystemPopup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.loadmate.app.shared.designsystem.theme.Pleos
import com.loadmate.app.shared.designsystem.components.PleosButton
import com.loadmate.app.shared.designsystem.components.PleosButtonStyle
import com.loadmate.app.shared.designsystem.components.Field
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.project.PleosTag
import com.loadmate.app.feature.assistant.domain.AssistantReply

@Composable
fun VoicePanel(s: VoiceUiState, on: (VoiceUiAction) -> Unit) {
    if (!s.open) return
    val c = Pleos.colors
    val t = Pleos.type
    val confirming = s.reply?.kind == AssistantReply.Kind.CONFIRM
    PleosSystemPopup(
        title = when (s.phase) {
            VoicePhase.IDLE -> "말씀하세요"
            VoicePhase.LISTENING -> "듣는 중…"
            VoicePhase.PROCESSING -> "처리 중…"
            VoicePhase.ANSWERED -> "응답"
            VoicePhase.CONFIRMING -> "확인 대기"
            VoicePhase.CLARIFYING -> "추가 질문"
            VoicePhase.FAILED -> if (s.sttReady) "다시 말하거나 직접 입력하세요" else "음성 사용 불가 · 직접 입력하세요"
        },
        onDismiss = { on(VoiceUiAction.Close) },
        size = PleosSystemPopupSize.Large,
        actions = if (confirming) {
            listOf(
                PleosPopupAction("확인", enabled = !s.busy) { on(VoiceUiAction.Confirm) },
                PleosPopupAction("취소", PleosButtonStyle.Basic) { on(VoiceUiAction.Cancel) },
                PleosPopupAction("다시 말하기", PleosButtonStyle.Basic) { on(VoiceUiAction.Listen) },
            )
        } else {
            listOf(
                PleosPopupAction(if (s.phase == VoicePhase.LISTENING) "듣는 중" else "다시 듣기", enabled = s.sttReady && s.phase != VoicePhase.LISTENING) {
                    on(VoiceUiAction.Listen)
                },
            )
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
            PleosTag("음성 인식 ON_DEVICE")
            PleosTag("해석: 규칙 기반")
        }
        (s.partial?.takeIf { s.phase == VoicePhase.LISTENING } ?: s.heard)?.let {
            BasicText("들은 말: \"$it\"", style = t.bodyLarge.copy(color = c.textSecondary))
        }
        s.reply?.let { r ->
            BasicText(r.display, style = t.titleSmall.copy(color = c.textPrimary))
            if (confirming) {
                BasicText("들은 말과 내용이 맞으면 확인을 누르세요. 확인 전에는 저장하지 않습니다.", style = t.labelMedium.copy(color = c.textTertiary))
            }
        }
        if (s.phase == VoicePhase.FAILED && s.sttError != null) {
            BasicText("음성 인식 상태: ${s.sttError}", style = t.labelMedium.copy(color = c.negative))
        }
        // F43 직접 입력 대체
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
            Field(s.typed, "직접 입력 (예: 다음 장소에 뭐 내려야 해)", Modifier.weight(1f), singleLine = true) { on(VoiceUiAction.Typed(it)) }
            PleosButton("보내기", style = PleosButtonStyle.Basic, enabled = s.typed.isNotBlank()) { on(VoiceUiAction.SubmitTyped) }
        }
    }
}
