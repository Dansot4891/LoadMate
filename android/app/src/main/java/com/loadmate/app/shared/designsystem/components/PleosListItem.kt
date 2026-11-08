package com.loadmate.app.shared.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loadmate.app.shared.designsystem.theme.Pleos
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing

/** 공식 Figma vehicle_setting_popup_content/List/item (높이 112, 제목 Bold 30/38, 부제 Regular 26/34) */
@Composable
fun PleosListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = Pleos.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .background(if (selected) c.dimDarkPrimary else Color.Transparent, RoundedCornerShape(PleosShape.medium))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = PleosSpacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PleosSpacing.l),
    ) {
        leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PleosSpacing.xs)) {
            BasicText(title, style = Pleos.type.bodyLarge.copy(color = c.textPrimary, fontWeight = FontWeight.Bold))
            subtitle?.let { BasicText(it, style = Pleos.type.bodySmall.copy(color = c.textTertiary)) }
        }
        trailing()
    }
}
