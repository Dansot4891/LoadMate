package com.loadmate.app.shared.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.theme.Pleos

/** Figma 아이콘(res/drawable/pleos_ic_*)을 Pleos 색으로 그린다 */
@Composable
fun PleosIcon(@DrawableRes icon: Int, contentDescription: String?, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Image(painterResource(icon), contentDescription, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** 공식 Figma ivi_icon_button 크기 */
enum class PleosIconButtonSize(val box: Dp, val icon: Dp, val radius: Dp) {
    Small(64.dp, 44.dp, 16.dp),
    Medium(80.dp, 56.dp, 20.dp),
}

enum class PleosIconButtonType { Basic, Plain, Circle }

/** 공식 Figma ivi_icon_button */
@Composable
fun PleosIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: PleosIconButtonSize = PleosIconButtonSize.Small,
    type: PleosIconButtonType = PleosIconButtonType.Basic,
    onClick: () -> Unit,
) {
    val c = Pleos.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = when {
        pressed && enabled -> c.buttonBasicPressed
        type == PleosIconButtonType.Plain -> Color.Transparent
        !enabled -> c.buttonBasicDisabled
        else -> c.buttonBasicEnabled
    }
    val shape = RoundedCornerShape(if (type == PleosIconButtonType.Circle) PleosShape.pill else size.radius)
    Box(
        modifier
            .size(size.box)
            .background(bg, shape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PleosIcon(icon, contentDescription, size.icon, if (enabled) c.iconPrimary else c.textQuaternary)
    }
}
