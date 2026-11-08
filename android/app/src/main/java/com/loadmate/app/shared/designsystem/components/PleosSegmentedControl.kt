package com.loadmate.app.shared.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.loadmate.app.shared.designsystem.theme.Pleos
import com.loadmate.app.shared.designsystem.dimens.PleosShadows
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.dimens.pleosShadow

/**
 * 하나를 고르는 묶음. 공식 Figma ivi_segmented_control/solid Medium (높이 80, 모서리 24).
 * 칸 수에 따른 최대 너비도 Figma 값(2~3칸 780, 4칸 960, 5칸 이상 1176)을 따른다.
 * PROJECT: Figma는 칸 너비가 같지만, 좁은 곳에서 긴 글자가 줄바꿈되지 않도록 글자 수에 비례해 나눈다.
 */
@Composable
fun <T> PleosSegmentedControl(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false,
    onSelect: (T) -> Unit,
) {
    val c = Pleos.colors
    val maxWidth = when {
        large && options.size <= 3 -> 880.dp
        options.size <= 3 -> 780.dp
        options.size == 4 && !large -> 960.dp
        else -> 1176.dp
    }
    Row(
        modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .heightIn(min = if (large) 100.dp else 80.dp)
            .background(c.tabBackground, RoundedCornerShape(PleosShape.large))
            .padding(PleosSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, option ->
            if (i > 0) Box(Modifier.width(PleosShape.hairline).height(36.dp).background(c.divider))
            Segment(label(option), option == selected, enabled, large, Modifier.weight(label(option).length + 2f)) { onSelect(option) }
        }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, enabled: Boolean, large: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Pleos.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(PleosShape.medium)
    val bg = when {
        selected -> c.buttonSwitchEnabled
        pressed -> c.buttonBasicPressed
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> c.textQuaternary
        selected -> c.textPrimary
        else -> c.textSecondary
    }
    Box(
        modifier
            .heightIn(min = if (large) 84.dp else PleosShape.minTouch)
            .then(if (selected) Modifier.pleosShadow(PleosShadows.level1, PleosShape.medium) else Modifier)
            .background(bg, shape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(horizontal = if (large) 28.dp else 18.dp, vertical = if (large) PleosSpacing.l else PleosSpacing.m),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = (if (large) Pleos.type.titleSmall else Pleos.type.bodyLarge).copy(color = fg, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
    }
}

/** 항목이 많아 옆으로 넘기는 선택 줄. 공식 Figma ivi_segmented_control/line (높이 100, 선택 밑줄 4) */
@Composable
fun <T> PleosLineSegmentedControl(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xl),
    ) {
        options.forEach { option ->
            LineSegment(label(option), option == selected) { onSelect(option) }
        }
    }
}

@Composable
private fun LineSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Pleos.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val underline = c.textPrimary
    Box(
        Modifier
            .heightIn(min = 100.dp)
            .drawBehind {
                if (selected) {
                    val w = PleosShape.tabUnderline.toPx()
                    drawRect(underline, Offset(0f, size.height - w), Size(size.width, w))
                }
            }
            .clickable(interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(vertical = PleosSpacing.m),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            Modifier
                .background(if (pressed) c.dimDarkPrimary else Color.Transparent, RoundedCornerShape(PleosShape.small))
                .padding(horizontal = 14.dp, vertical = 18.dp),
            style = Pleos.type.titleSmall.copy(color = if (selected) c.textPrimary else c.textSecondary),
        )
    }
}
