package com.loadmate.app.shared.designsystem.project

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.loadmate.app.R
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.theme.Pleos

/** PROJECT: Loadmate identity in the trip list header. */
@Composable
fun LoadmateBrand() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(PleosShape.small))) {
            Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.matchParentSize())
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.matchParentSize())
        }
        BasicText("Loadmate", style = Pleos.type.titleSmall.copy(color = Pleos.colors.textPrimary))
    }
}
