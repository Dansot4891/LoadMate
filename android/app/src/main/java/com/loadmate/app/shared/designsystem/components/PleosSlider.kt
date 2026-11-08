package com.loadmate.app.shared.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.loadmate.app.R
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.theme.Pleos

enum class PleosSliderSize(
    val height: Dp, val horizontalPadding: Dp, val verticalPadding: Dp, val gap: Dp,
    val track: Dp, val radius: Dp, val trackRadius: Dp, val fillRadius: Dp, val button: PleosIconButtonSize, val maxWidth: Dp,
) {
    Medium(80.dp, 10.dp, 8.dp, 12.dp, 64.dp, 24.dp, 20.dp, 16.dp, PleosIconButtonSize.Small, 560.dp),
    Large(100.dp, 20.dp, 10.dp, 16.dp, 80.dp, 32.dp, 24.dp, 20.dp, PleosIconButtonSize.Medium, 808.dp),
}

private fun Modifier.sliderInput(width: Float, current: () -> Float, onChange: (Float) -> Unit) = this
    .pointerInput(width) { detectTapGestures { onChange((it.x / width).coerceIn(0f, 1f)) } }
    .pointerInput(width) {
        detectHorizontalDragGestures { _, drag -> onChange((current() + drag / width).coerceIn(0f, 1f)) }
    }

/** 공식 Figma ivi_slider_continuous. value 는 0..1 */
@Composable
fun PleosSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    size: PleosSliderSize = PleosSliderSize.Medium,
    floating: Boolean = false,
    label: String? = null,
    step: Float = 0.1f,
) {
    val c = Pleos.colors
    val shape = RoundedCornerShape(size.radius)
    val latest by rememberUpdatedState(value)
    Row(
        modifier
            .widthIn(max = size.maxWidth)
            .fillMaxWidth()
            .heightIn(min = size.height)
            .background(if (floating) c.backgroundPopup else c.dimDarkPrimary, shape)
            .then(if (floating) Modifier.border(PleosShape.hairline, c.divider, shape) else Modifier)
            .padding(horizontal = size.horizontalPadding, vertical = size.verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(size.gap),
    ) {
        label?.let { BasicText(it, style = Pleos.type.bodyLarge.copy(color = c.textSecondary, fontWeight = FontWeight.Bold)) }
        PleosIconButton(R.drawable.pleos_ic_minus, "줄이기", size = size.button, type = PleosIconButtonType.Plain) {
            onValueChange((latest - step).coerceIn(0f, 1f))
        }
        BoxWithConstraints(
            Modifier.weight(1f).height(size.track).background(c.sliderNormal, RoundedCornerShape(size.trackRadius)),
        ) {
            val width = constraints.maxWidth.toFloat()
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .padding(4.dp)
                    .background(c.sliderKnob, RoundedCornerShape(size.fillRadius)),
            )
            Box(Modifier.matchParentSize().sliderInput(width, { latest }, onValueChange))
        }
        PleosIconButton(R.drawable.pleos_ic_plus, "늘리기", size = size.button, type = PleosIconButtonType.Plain) {
            onValueChange((latest + step).coerceIn(0f, 1f))
        }
    }
}

/** 공식 Figma ivi_slider_centered 종류 */
enum class PleosCenteredSliderType { Basic, Button, Number }

/** 공식 Figma ivi_slider_centered (실내 온도형). value 는 0..1, off 면 회색 */
@Composable
fun PleosCenteredSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    type: PleosCenteredSliderType = PleosCenteredSliderType.Basic,
    off: Boolean = false,
    number: String? = null,
    step: Float = 0.1f,
) {
    val c = Pleos.colors
    val shape = RoundedCornerShape(32.dp)
    val latest by rememberUpdatedState(value)
    val button = type == PleosCenteredSliderType.Button
    Row(
        modifier
            .heightIn(min = 100.dp)
            .background(c.climateSliderBackground, shape)
            .border(3.dp, c.climateButtonBorder, shape)
            .padding(horizontal = if (button) 10.dp else 40.dp, vertical = if (button) 10.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (type == PleosCenteredSliderType.Number) {
            BasicText(number.orEmpty(), style = Pleos.type.headlineLarge.copy(color = c.textPrimary, fontWeight = FontWeight.Bold))
        }
        if (button) {
            PleosIconButton(R.drawable.pleos_ic_minus, "줄이기", size = PleosIconButtonSize.Medium, type = PleosIconButtonType.Plain) {
                onValueChange((latest - step).coerceIn(0f, 1f))
            }
        }
        BoxWithConstraints(Modifier.widthIn(min = 320.dp, max = 320.dp).height(48.dp), contentAlignment = Alignment.CenterStart) {
            val width = constraints.maxWidth.toFloat()
            val track = if (off) Brush.linearGradient(listOf(c.surfaceAccent, c.surfaceAccent)) else Brush.horizontalGradient(listOf(c.regulationBlue, c.regulationRed))
            Box(Modifier.fillMaxWidth().height(8.dp).background(track, RoundedCornerShape(PleosShape.pill)))
            Box(
                Modifier
                    .offset(x = (maxWidth - 48.dp) * value.coerceIn(0f, 1f))
                    .size(48.dp)
                    .background(c.basic700, CircleShape)
                    .border(4.dp, c.climateSliderBackground, CircleShape),
            )
            Box(Modifier.matchParentSize().sliderInput(width, { latest }, onValueChange))
        }
        if (button) {
            PleosIconButton(R.drawable.pleos_ic_plus, "늘리기", size = PleosIconButtonSize.Medium, type = PleosIconButtonType.Plain) {
                onValueChange((latest + step).coerceIn(0f, 1f))
            }
        }
    }
}
