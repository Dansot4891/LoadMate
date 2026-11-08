package com.loadmate.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.SolidColor

@Composable
fun Field(value: String, hint: String, modifier: Modifier, onChange: (String) -> Unit) {
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
