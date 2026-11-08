package com.loadmate.app.shared.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.theme.Pleos

enum class PleosButtonStyle { Filled, Basic }

/** 공식 Figma ivi_basic_button */
enum class PleosButtonSize(val height: Dp, val minWidth: Dp, val horizontalPadding: Dp, val radius: Dp, val iconSize: Dp, val iconGap: Dp) {
    Small(64.dp, 100.dp, 24.dp, 16.dp, 32.dp, 12.dp),
    Medium(80.dp, 120.dp, 32.dp, 24.dp, 40.dp, 14.dp),
    Large(100.dp, 140.dp, 40.dp, 32.dp, 52.dp, 16.dp),
}

@Composable
fun PleosButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: PleosButtonStyle = PleosButtonStyle.Filled,
    size: PleosButtonSize = PleosButtonSize.Medium,
    @DrawableRes leadingIcon: Int? = null,
    onClick: () -> Unit,
) {
    val c = Pleos.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val filled = style == PleosButtonStyle.Filled
    val bg = when {
        !enabled -> if (filled) c.buttonFilledDisabled else c.buttonBasicDisabled
        pressed -> if (filled) c.buttonFilledPressed else c.buttonBasicPressed
        else -> if (filled) c.buttonFilledEnabled else c.buttonBasicEnabled
    }
    val fg = when {
        !enabled -> c.textQuaternary
        filled -> c.onFilled
        else -> c.textPrimary
    }
    val type = when (size) {
        PleosButtonSize.Small -> Pleos.type.bodySmall
        PleosButtonSize.Medium -> Pleos.type.bodyLarge
        PleosButtonSize.Large -> Pleos.type.titleSmall
    }
    val shape = RoundedCornerShape(size.radius)
    Box(
        modifier = modifier
            .heightIn(min = size.height)
            .widthIn(min = size.minWidth)
            .background(bg, shape)
            .then(if (enabled) Modifier else Modifier.border(PleosShape.hairline, c.divider, shape))
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = size.horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(size.iconGap)) {
            leadingIcon?.let { PleosIcon(it, null, size.iconSize, fg) }
            BasicText(label, style = type.copy(color = fg, fontWeight = FontWeight.Bold))
        }
    }
}

/** 공식 Figma ivi_text_button Medium */
@Composable
fun PleosTextButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Pleos.colors
    Box(
        modifier = modifier
            .heightIn(min = PleosShape.minTouch)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = PleosSpacing.s),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = Pleos.type.bodyLarge.copy(color = if (enabled) c.positive else c.textQuaternary, fontWeight = FontWeight.Bold))
    }
}

/** 공식 Figma ivi_toggle_button. 켜짐은 흰 배경이므로 회색 바탕 위에서 쓴다 */
@Composable
fun PleosToggleButton(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: PleosButtonSize = PleosButtonSize.Medium,
) {
    val c = Pleos.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = when {
        checked && !enabled -> c.buttonSwitchDisabled
        checked && pressed -> c.buttonSwitchPressed
        checked -> c.buttonSwitchEnabled
        !enabled -> c.buttonBasicDisabled
        pressed -> c.buttonBasicPressed
        else -> c.buttonBasicEnabled
    }
    val fg = when {
        !enabled -> c.textQuaternary
        checked -> c.textPrimary
        else -> c.textSecondary
    }
    val type = when (size) {
        PleosButtonSize.Small -> Pleos.type.bodySmall
        PleosButtonSize.Medium -> Pleos.type.bodyLarge
        PleosButtonSize.Large -> Pleos.type.titleSmall
    }
    val shape = RoundedCornerShape(size.radius)
    Box(
        modifier
            .heightIn(min = size.height)
            .widthIn(min = size.minWidth)
            .background(bg, shape)
            .then(if (enabled) Modifier else Modifier.border(PleosShape.hairline, c.divider, shape))
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = size.horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = type.copy(color = fg, fontWeight = FontWeight.Bold))
    }
}
