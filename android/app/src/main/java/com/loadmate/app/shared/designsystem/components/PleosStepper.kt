package com.loadmate.app.shared.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.loadmate.app.R
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.theme.Pleos

/** 수량 조절. 공식 Figma ivi_stepper (420×100) */
@Composable
fun PleosStepper(label: String, value: Int, onChange: (Int) -> Unit, min: Int = 0, max: Int = 99) {
    val c = Pleos.colors
    val shape = RoundedCornerShape(PleosShape.large)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.l)) {
        BasicText(label, style = Pleos.type.bodyLarge.copy(color = c.textPrimary))
        Row(
            Modifier
                .width(420.dp)
                .heightIn(min = 100.dp)
                .background(c.stepperBackground, shape)
                .border(PleosShape.hairline, c.divider, shape)
                .padding(horizontal = PleosSpacing.xl, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xl),
        ) {
            PleosIconButton(R.drawable.pleos_ic_minus, "$label 줄이기", enabled = value > min) { onChange(value - 1) }
            StepDivider()
            BasicText(
                "$value",
                Modifier.weight(1f),
                style = Pleos.type.titleMedium.copy(color = c.textPrimary, textAlign = TextAlign.Center),
            )
            StepDivider()
            PleosIconButton(R.drawable.pleos_ic_plus, "$label 늘리기", enabled = value < max) { onChange(value + 1) }
        }
    }
}

@Composable
private fun StepDivider() {
    Box(Modifier.width(PleosShape.hairline).height(PleosShape.minTouch).background(Pleos.colors.dividerTertiary))
}
