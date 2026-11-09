package com.loadmate.app.shared.designsystem.project

import androidx.compose.ui.graphics.Color
import com.loadmate.app.shared.designsystem.color.PleosColors

/** PROJECT: 적재 화면의 방문지 구분색. 값은 공식 토큰을 쓰고, 빨강(negative)은 문제 표시용으로 남겨 둔다 */
fun PleosColors.stopColor(label: String?): Color {
    if (label.isNullOrEmpty()) return basic700
    val palette = listOf(active, regulationOrange, positive, autonomous, regulationYellow, regulationBlue)
    return palette[(label[0].code - 'A'.code).mod(palette.size)]
}
