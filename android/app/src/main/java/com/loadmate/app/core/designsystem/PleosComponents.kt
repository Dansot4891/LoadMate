package com.loadmate.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** PROJECT: 공식 컴포넌트 규격 미확인 → 공식 색상·타이포 토큰만 사용한 프로젝트 컴포넌트. */

enum class PleosButtonStyle { Filled, Basic }

@Composable
fun PleosButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: PleosButtonStyle = PleosButtonStyle.Filled,
    onClick: () -> Unit,
) {
    val c = Pleos.colors
    val (bg, fg) = when {
        !enabled -> c.buttonFilledDisabled to c.textQuaternary
        style == PleosButtonStyle.Filled -> c.buttonFilledEnabled to c.onFilled
        else -> c.buttonBasicEnabled to c.textPrimary
    }
    Box(
        modifier = modifier
            .heightIn(min = PleosShape.minTouch)
            .background(bg, RoundedCornerShape(PleosShape.pill))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = PleosSpacing.xxl),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = Pleos.type.bodyMedium.copy(color = fg))
    }
}

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

/** 출처·상태 표시용 작은 꼬리표 (예: "샘플 데이터", "에뮬레이터 신호") */
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

/** 수량 조절 (− 값 +). 차량 터치 타깃 최소 크기 사용 */
@Composable
fun PleosStepper(label: String, value: Int, onChange: (Int) -> Unit, min: Int = 0, max: Int = 99) {
    val c = Pleos.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.l)) {
        BasicText(label, style = Pleos.type.bodyLarge.copy(color = c.textPrimary))
        StepButton("−", enabled = value > min) { onChange(value - 1) }
        BasicText("$value", style = Pleos.type.titleMedium.copy(color = c.textPrimary))
        StepButton("+", enabled = value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Pleos.colors
    Box(
        modifier = Modifier
            .size(PleosShape.minTouch)
            .background(if (enabled) c.buttonBasicEnabled else c.buttonBasicDisabled, RoundedCornerShape(PleosShape.pill))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = Pleos.type.titleSmall.copy(color = if (enabled) c.textPrimary else c.textQuaternary))
    }
}
