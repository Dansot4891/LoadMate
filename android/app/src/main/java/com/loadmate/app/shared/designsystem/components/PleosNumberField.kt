package com.loadmate.app.shared.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.loadmate.app.shared.designsystem.theme.Pleos

enum class PleosNumberFieldSize(val width: Dp, val height: Dp) { Medium(128.dp, 128.dp), Large(140.dp, 180.dp) }

/** 공식 Figma ivi_number_field. 숫자 한 칸 입력 (모서리 32, 테두리 4) */
@Composable
fun PleosNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    size: PleosNumberFieldSize = PleosNumberFieldSize.Medium,
    isError: Boolean = false,
) {
    val c = Pleos.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(32.dp)
    val stroke = when {
        isError -> c.regulationRed
        focused -> c.fieldFocused
        else -> c.divider
    }
    Box(
        modifier
            .size(size.width, size.height)
            .background(if (focused) c.dimDarkSecondary else Color.Transparent, shape)
            .border(4.dp, stroke, shape),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value,
            { onValueChange(it.filter(Char::isDigit).takeLast(1)) },
            Modifier.fillMaxWidth(),
            textStyle = Pleos.type.headlineLarge.copy(color = c.textPrimary, textAlign = TextAlign.Center, fontWeight = FontWeight.ExtraBold),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            interactionSource = interaction,
            cursorBrush = SolidColor(c.iconPrimary),
        )
    }
}
