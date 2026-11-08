package com.loadmate.app.shared.designsystem.project

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.theme.Pleos

/** PROJECT: 출처·상태 표시용 작은 꼬리표 (예: "샘플 데이터", "에뮬레이터 신호") */
@Composable
fun PleosTag(text: String, color: Color = Pleos.colors.textSecondary) {
    BasicText(
        text,
        modifier = Modifier
            .border(width = PleosShape.hairline, color = color, shape = RoundedCornerShape(PleosShape.small))
            .padding(horizontal = PleosSpacing.s, vertical = PleosSpacing.xs),
        style = Pleos.type.labelMedium.copy(color = color),
    )
}
