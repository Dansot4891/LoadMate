package com.loadmate.app.shared.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.loadmate.app.R
import com.loadmate.app.shared.designsystem.theme.Pleos

enum class PleosCheckboxState { Off, On, Indeterminate }

/** 공식 Figma ivi_checkbox (터치 64, 상자 48, 모서리 12) */
@Composable
fun PleosCheckbox(state: PleosCheckboxState, onToggle: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Pleos.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(12.dp)
    val active = state != PleosCheckboxState.Off
    val bg = when {
        active && !enabled -> c.switchDisabled
        active -> c.switchOn
        !enabled -> c.dimDarkPrimary
        pressed -> c.dimDarkSecondary
        else -> Color.Transparent
    }
    Box(
        modifier.size(64.dp).toggleable(state == PleosCheckboxState.On, interaction, null, enabled, Role.Checkbox) { onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(48.dp).background(bg, shape)
                .then(if (active) Modifier else Modifier.border(4.dp, if (enabled) c.dividerTertiary else c.divider, shape)),
            contentAlignment = Alignment.Center,
        ) {
            if (active) PleosIcon(if (state == PleosCheckboxState.On) R.drawable.pleos_ic_check_basic else R.drawable.pleos_ic_minus, null, 44.dp, c.switchKnobOn)
        }
    }
}

/** 공식 Figma ivi_radio_button (터치 64, 원 48) */
@Composable
fun PleosRadioButton(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Pleos.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier.size(64.dp).selectable(selected, interaction, null, enabled, Role.RadioButton, onClick),
        contentAlignment = Alignment.Center,
    ) {
        val bg = when {
            selected -> if (enabled) c.switchKnobOn else c.switchKnobDisabled
            pressed -> c.dimDarkSecondary
            else -> Color.Transparent
        }
        val stroke = when {
            selected && enabled -> c.active
            selected -> c.switchDisabled
            else -> c.dividerTertiary
        }
        Box(Modifier.size(48.dp).background(bg, CircleShape).border(if (selected) 12.dp else 4.dp, stroke, CircleShape))
    }
}
