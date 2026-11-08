package com.loadmate.app.feature.settings.presentation.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.loadmate.app.shared.designsystem.theme.Pleos
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing

/** 항목 한 줄의 표시 내용. note 는 이름 아래 작은 글씨 (실패 사유·대체 방법 등) */
data class StatusLine(val name: String, val value: String, val tone: Color, val note: String? = null)

/** 상태 점 · 항목 이름 · 오른쪽 상태 값. */
@Composable
fun StatusRow(line: StatusLine, trailing: (@Composable () -> Unit)? = null) {
    val c = Pleos.colors
    val t = Pleos.type
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PleosSpacing.l),
    ) {
        Box(Modifier.size(PleosSpacing.l).background(line.tone, RoundedCornerShape(PleosShape.pill)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PleosSpacing.xs)) {
            BasicText(line.name, style = t.bodyLarge.copy(color = c.textPrimary))
            line.note?.let { BasicText(it, style = t.labelMedium.copy(color = c.textTertiary)) }
        }
        BasicText(line.value, style = t.bodyLarge.copy(color = line.tone, fontWeight = FontWeight.Bold))
        trailing?.invoke()
    }
}
