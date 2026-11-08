package com.loadmate.app.shared.designsystem.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.theme.Pleos

/** PROJECT: 공식 Figma 에 대응 규격이 없는 내용 묶음 */
@Composable
fun PleosCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Pleos.colors.surfaceBasic, RoundedCornerShape(PleosShape.medium))
            .padding(PleosSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(PleosSpacing.m),
        content = content,
    )
}
