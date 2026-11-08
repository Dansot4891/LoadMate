package com.loadmate.app.shared.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loadmate.app.R
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.theme.Pleos

/** 공식 Figma ivi_title Search (높이 100). 뒤로 + 검색 입력 + 검색 버튼 */
@Composable
fun PleosSearchTitle(value: String, hint: String, onValueChange: (String) -> Unit, onBack: () -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xl)) {
        PleosIconButton(R.drawable.pleos_ic_arrow_left, "뒤로", size = PleosIconButtonSize.Medium, type = PleosIconButtonType.Plain, onClick = onBack)
        Field(value, hint, Modifier.weight(1f), singleLine = true, onChange = onValueChange)
        PleosButton("검색", style = PleosButtonStyle.Basic, onClick = onSearch)
    }
}

/** 공식 Figma ivi_header. 제목 줄 아래에 선택 줄을 넣을 수 있다 (상단 40, 하단 24) */
@Composable
fun PleosHeader(title: @Composable () -> Unit, modifier: Modifier = Modifier, content: (@Composable ColumnScope.() -> Unit)? = null) {
    val c = Pleos.colors
    Column(
        modifier.fillMaxWidth().background(c.surfaceBasic).border(1.dp, c.dividerTertiary).padding(start = 40.dp, end = 24.dp, top = 40.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(PleosSpacing.m),
    ) {
        title()
        content?.invoke(this)
    }
}

/** 공식 Figma ivi_app_bar (창을 끄는 손잡이, 320×60, 막대 140×8) */
@Composable
fun PleosAppBar(modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit = {}) {
    val c = Pleos.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier.size(320.dp, 60.dp).clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(140.dp, 8.dp).background(if (pressed) c.alphaDark500 else c.alphaDark200, RoundedCornerShape(PleosShape.pill)))
    }
}

/** 공식 Figma ivi_scroll_bar (표시용, 6×150) */
@Composable
fun PleosScrollIndicator(modifier: Modifier = Modifier, pressed: Boolean = false) {
    Box(modifier.size(6.dp, 150.dp).background(if (pressed) Pleos.colors.alphaDark500 else Pleos.colors.alphaDark200, RoundedCornerShape(PleosShape.pill)))
}

/** 공식 Figma ivi_scroll_bar Controllable (위·아래 버튼과 막대) */
@Composable
fun PleosScrollControl(onUp: () -> Unit, onDown: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(96.dp).padding(horizontal = 12.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PleosIconButton(R.drawable.pleos_ic_arrow_up, "위로", onClick = onUp)
        Box(Modifier.size(8.dp, 127.dp).background(Pleos.colors.alphaDark500, RoundedCornerShape(PleosShape.pill)))
        PleosIconButton(R.drawable.pleos_ic_arrow_down, "아래로", onClick = onDown)
    }
}

enum class PleosNotificationButtons { Single, Horizontal, Vertical, Call }

/** 공식 Figma ivi_notification_system (너비 700, 모서리 48) */
@Composable
fun PleosSystemNotification(
    title: String,
    description: String,
    actions: List<PleosPopupAction>,
    modifier: Modifier = Modifier,
    buttons: PleosNotificationButtons = PleosNotificationButtons.Horizontal,
    image: (@Composable () -> Unit)? = null,
) {
    val c = Pleos.colors
    val shape = RoundedCornerShape(48.dp)
    Column(modifier.width(700.dp).background(c.backgroundPopup, shape).border(1.dp, c.divider, shape)) {
        Row(Modifier.fillMaxWidth().padding(32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xl)) {
            image?.invoke()
            Column(Modifier.weight(1f).padding(vertical = PleosSpacing.xs), verticalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
                BasicText(title, style = Pleos.type.titleMedium.copy(color = c.textPrimary, fontWeight = FontWeight.ExtraBold))
                BasicText(description, style = Pleos.type.bodySmall.copy(color = c.textTertiary))
            }
        }
        val pad = Modifier.fillMaxWidth().padding(PleosSpacing.xl)
        if (buttons == PleosNotificationButtons.Vertical) {
            Column(pad, verticalArrangement = Arrangement.spacedBy(PleosSpacing.l)) {
                actions.forEach { PleosButton(it.label, Modifier.fillMaxWidth(), it.enabled, it.style, onClick = it.onClick) }
            }
        } else {
            Row(pad, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.l)) {
                actions.forEachIndexed { i, a ->
                    if (buttons == PleosNotificationButtons.Call) CallButton(a, i == 0, Modifier.weight(1f))
                    else PleosButton(a.label, Modifier.weight(1f), a.enabled, a.style, onClick = a.onClick)
                }
            }
        }
    }
}

@Composable
private fun CallButton(action: PleosPopupAction, accept: Boolean, modifier: Modifier) {
    val c = Pleos.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = when {
        accept && pressed -> c.callAcceptPressed
        accept -> c.callAcceptNormal
        pressed -> c.callEndPressed
        else -> c.callEndNormal
    }
    Box(
        modifier.height(PleosButtonSize.Medium.height).background(bg, RoundedCornerShape(PleosButtonSize.Medium.radius))
            .clickable(interaction, indication = null, role = Role.Button, onClick = action.onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(action.label, style = Pleos.type.bodyLarge.copy(color = c.onStatic, fontWeight = FontWeight.Bold))
    }
}
