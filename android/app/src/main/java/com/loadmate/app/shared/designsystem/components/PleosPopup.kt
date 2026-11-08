package com.loadmate.app.shared.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.loadmate.app.R
import com.loadmate.app.shared.designsystem.theme.Pleos
import com.loadmate.app.shared.designsystem.components.PleosButton
import com.loadmate.app.shared.designsystem.components.PleosButtonSize
import com.loadmate.app.shared.designsystem.components.PleosButtonStyle
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing

/** 공식 Figma ivi_system_popup 너비 */
enum class PleosSystemPopupSize(val width: Dp) { Medium(808.dp), Large(1200.dp) }

/** 공식 Figma system_popup/buttons 배치 */
enum class PleosPopupButtons { Horizontal, Vertical }

data class PleosPopupAction(
    val label: String,
    val style: PleosButtonStyle = PleosButtonStyle.Filled,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * 공식 Figma ivi_system_popup: 닫기 → 제목 → 내용 → 버튼 (구역 간격 40, 안쪽 여백 48, 모서리 48).
 * 바깥이나 닫기를 누르면 onDismiss.
 */
@Composable
fun PleosSystemPopup(
    title: String,
    onDismiss: () -> Unit,
    actions: List<PleosPopupAction>,
    size: PleosSystemPopupSize = PleosSystemPopupSize.Medium,
    buttons: PleosPopupButtons = PleosPopupButtons.Horizontal,
    extended: Boolean = false,
    @DrawableRes icon: Int? = null,
    subtitle: String? = null,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val c = Pleos.colors
    val shape = RoundedCornerShape(if (extended) 40.dp else PleosShape.popup)
    val padding = if (extended) 40.dp else PleosSpacing.popupPadding
    val width = if (extended) (if (size == PleosSystemPopupSize.Medium) 720.dp else 1000.dp) else size.width
    Box(
        Modifier.fillMaxSize().background(c.overlayDefault).clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(PleosSpacing.xl)
                .widthIn(max = width)
                .fillMaxWidth()
                .heightIn(max = 1152.dp)
                .background(c.backgroundPopup, shape)
                .border(PleosShape.hairline, c.dividerPrimary, shape)
                .clip(shape)
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(if (extended) 33.dp else 40.dp),
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PleosSpacing.xl)) {
                Box(
                    Modifier
                        .align(Alignment.End)
                        .size(PleosShape.minTouch)
                        .clickable(role = Role.Button, onClickLabel = "닫기", onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    PleosIcon(R.drawable.pleos_ic_close, "닫기", 48.dp, c.iconPrimary)
                }
                icon?.let { PleosIcon(it, null, 80.dp, c.iconPrimary) }
                BasicText(title, style = Pleos.type.titleLargeExtraStrong.copy(color = c.textPrimary))
                subtitle?.let { BasicText(it, style = Pleos.type.titleMedium.copy(color = c.textSecondary)) }
                description?.let { BasicText(it, style = Pleos.type.bodyLarge.copy(color = c.textSecondary)) }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PleosSpacing.xl), content = content)
            if (actions.isNotEmpty()) when (buttons) {
                PleosPopupButtons.Horizontal -> Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xl)) {
                    actions.forEach { ActionButton(it, Modifier.weight(1f)) }
                }
                PleosPopupButtons.Vertical -> Column(verticalArrangement = Arrangement.spacedBy(PleosSpacing.xl)) {
                    actions.forEach { ActionButton(it, Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(action: PleosPopupAction, modifier: Modifier) {
    PleosButton(action.label, modifier, action.enabled, action.style, PleosButtonSize.Large, onClick = action.onClick)
}
