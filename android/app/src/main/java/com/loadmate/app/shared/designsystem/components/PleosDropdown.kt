package com.loadmate.app.shared.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.loadmate.app.R
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.theme.Pleos

enum class PleosDropdownSize(val height: Dp, val radius: Dp, val horizontalPadding: Dp) {
    Small(80.dp, 28.dp, 16.dp),
    Medium(100.dp, 32.dp, 20.dp),
}

/** 공식 Figma ivi_dropdown + ivi_dropdown_menus */
@Composable
fun <T> PleosDropdown(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    placeholder: String,
    modifier: Modifier = Modifier,
    size: PleosDropdownSize = PleosDropdownSize.Medium,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    val c = Pleos.colors
    var open by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    var heightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(size.radius)
    Box(modifier.onSizeChanged { widthPx = it.width; heightPx = it.height }) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = size.height)
                .background(if (pressed) c.dropdownPressed else c.dropdownNormal, shape)
                .border(PleosShape.hairline, c.divider, shape)
                .clickable(interaction, indication = null, enabled = enabled, role = Role.DropdownList) { open = !open }
                .padding(horizontal = size.horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                selected?.let(label) ?: placeholder,
                Modifier.weight(1f).padding(horizontal = PleosShape.minTouch / 4),
                style = Pleos.type.bodyLarge.copy(color = if (enabled) c.textPrimary else c.textQuaternary, fontWeight = FontWeight.Bold),
            )
            PleosIcon(R.drawable.pleos_ic_dropdown_down, null, 48.dp, if (enabled) c.iconPrimary else c.textQuaternary)
        }
        if (open) {
            val offset = with(density) { IntOffset(0, heightPx + 8.dp.roundToPx()) }
            Popup(offset = offset, onDismissRequest = { open = false }, properties = PopupProperties(focusable = true)) {
                val width = with(density) { widthPx.toDp() }
                Column(
                    Modifier
                        .width(width)
                        .heightIn(max = 600.dp)
                        .background(c.surfaceBasic, shape)
                        .border(PleosShape.hairline, c.divider, shape)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    options.forEach { option ->
                        val isSelected = option == selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = size.height)
                                .background(if (isSelected) c.dimDarkPrimary else Color.Transparent, shape)
                                .clickable(role = Role.Button) { open = false; onSelect(option) }
                                .padding(horizontal = size.horizontalPadding),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BasicText(label(option), Modifier.weight(1f), style = Pleos.type.bodyLarge.copy(color = c.textPrimary, fontWeight = FontWeight.Bold))
                            if (isSelected) PleosIcon(R.drawable.pleos_ic_check_basic, null, 48.dp, c.iconPrimary)
                        }
                    }
                }
            }
        }
    }
}
