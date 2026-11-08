package com.loadmate.app.shared.designsystem.components

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.theme.Pleos

enum class PleosFieldSize(val height: Dp, val horizontalPadding: Dp, val gap: Dp) {
    Small(80.dp, 24.dp, 12.dp),
    Medium(100.dp, 32.dp, 16.dp),
}

/** 공식 Figma ivi_text_field (모서리 24). helpText 는 아래 안내, isError 면 빨강, readOnly 면 수정 불가 */
@Composable
fun Field(
    value: String,
    hint: String,
    modifier: Modifier,
    singleLine: Boolean = false,
    size: PleosFieldSize = PleosFieldSize.Small,
    helpText: String? = null,
    isError: Boolean = false,
    readOnly: Boolean = false,
    onChange: (String) -> Unit,
) {
    val c = Pleos.colors
    val t = Pleos.type
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(PleosShape.large)
    val style = if (size == PleosFieldSize.Medium) t.titleMediumNormal else t.bodyLarge
    val stroke = when {
        isError -> c.regulationRed
        focused && !readOnly -> c.fieldFocused
        else -> c.divider
    }
    val width = if (isError || (focused && !readOnly)) PleosShape.focusBorder else PleosShape.hairline
    Column(modifier, verticalArrangement = Arrangement.spacedBy(size.gap)) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = size.height)
                .background(if (readOnly) c.surfaceHigh else c.fieldBackground, shape)
                .border(width, stroke, shape)
                .padding(horizontal = size.horizontalPadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                BasicText(
                    hint,
                    style = style.copy(color = c.textTertiary),
                    maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value, onChange, Modifier.fillMaxWidth(),
                readOnly = readOnly,
                textStyle = style.copy(color = c.textPrimary),
                singleLine = singleLine,
                interactionSource = interaction,
                cursorBrush = SolidColor(c.textPrimary),
            )
        }
        helpText?.let {
            BasicText(it, Modifier.padding(horizontal = 16.dp), style = t.bodySmall.copy(color = if (isError) c.regulationRed else c.textSecondary))
        }
    }
}
