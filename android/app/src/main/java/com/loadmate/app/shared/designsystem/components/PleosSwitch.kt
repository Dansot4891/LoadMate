package com.loadmate.app.shared.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.theme.Pleos

/** 공식 Figma ivi_switch 크기 */
enum class PleosSwitchSize(val width: Dp, val height: Dp, val radius: Dp, val padding: Dp, val knob: Dp, val knobRadius: Dp) {
    Small(64.dp, 40.dp, 12.dp, 6.dp, 28.dp, 6.dp),
    Medium(80.dp, 48.dp, 16.dp, 8.dp, 32.dp, 8.dp),
}

/** 공식 Figma ivi_switch. 터치 영역은 높이 64 */
@Composable
fun PleosSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: PleosSwitchSize = PleosSwitchSize.Medium,
) {
    val c = Pleos.colors
    val track = when {
        !enabled -> c.switchDisabled
        checked -> c.switchOn
        else -> c.switchOff
    }
    val knob = when {
        !enabled -> c.switchKnobDisabled
        checked -> c.switchKnobOn
        else -> c.switchKnobOff
    }
    Box(
        modifier
            .heightIn(min = PleosShape.minTouch)
            .toggleable(checked, remember { MutableInteractionSource() }, null, enabled, Role.Switch, onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = size.width, height = size.height)
                .background(track, RoundedCornerShape(size.radius))
                .padding(size.padding),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(size.knob).background(knob, RoundedCornerShape(size.knobRadius)))
        }
    }
}
