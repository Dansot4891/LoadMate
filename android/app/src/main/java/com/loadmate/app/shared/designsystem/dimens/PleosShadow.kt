package com.loadmate.app.shared.designsystem.dimens

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 공식 Shadow (foundations/shadow) — X/Y/Blur/Color. blur→Android shadowLayer radius는 blur/2로 변환(PROJECT 해석). */
data class ShadowLayer(val x: Float, val y: Float, val blur: Float, val color: Color)

object PleosShadows {
    val level1 = listOf(ShadowLayer(0f, 6f, 24f, Color(0x14000000)))
    val level2 = listOf(ShadowLayer(0f, 8f, 32f, Color(0x29000000)), ShadowLayer(0f, 2f, 2f, Color(0x14000000)))
    val level3 = listOf(ShadowLayer(0f, 20f, 40f, Color(0x3D000000)), ShadowLayer(0f, 2f, 4f, Color(0x33000000)))
}

fun Modifier.pleosShadow(layers: List<ShadowLayer>, corner: Dp): Modifier = drawBehind {
    drawIntoCanvas { canvas ->
        layers.forEach { l ->
            val paint = Paint()
            val fp = paint.asFrameworkPaint()
            fp.color = android.graphics.Color.TRANSPARENT
            fp.setShadowLayer(l.blur.dp.toPx() / 2f, l.x.dp.toPx(), l.y.dp.toPx(), l.color.toArgb())
            canvas.drawRoundRect(0f, 0f, size.width, size.height, corner.toPx(), corner.toPx(), paint)
        }
    }
}
