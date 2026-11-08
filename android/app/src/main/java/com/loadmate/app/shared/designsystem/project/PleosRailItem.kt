package com.loadmate.app.shared.designsystem.project

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loadmate.app.shared.designsystem.components.PleosIcon
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.theme.Pleos

/** 왼쪽 세로 메뉴 항목. 아이콘 위·글자 아래. 공식 Figma 에 대응 규격이 없는 PROJECT 컴포넌트 */
@Composable
fun PleosRailItem(
    label: String,
    @DrawableRes icon: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    role: Role = Role.Tab,
    onClick: () -> Unit,
) {
    val c = Pleos.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = when {
        selected -> c.buttonFilledEnabled
        pressed -> c.buttonBasicPressed
        else -> Color.Transparent
    }
    val fg = if (selected) c.onFilled else c.textSecondary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(PleosShape.large))
            .clickable(interaction, indication = null, role = role, onClick = onClick)
            .padding(vertical = PleosSpacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PleosSpacing.s),
    ) {
        PleosIcon(icon, null, 40.dp, fg)
        BasicText(label, style = Pleos.type.bodySmall.copy(color = fg, fontWeight = FontWeight.Bold))
    }
}
