package com.loadmate.app.feature.trip.presentation.driving.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.loadmate.app.core.designsystem.Pleos
import com.loadmate.app.feature.trip.domain.Stop

/** 방문지 좌표 개요. 도로 경로나 현재 위치를 나타내지 않는다. */
@Composable
internal fun RouteOverview(stops: List<Stop>) {
    if (stops.isEmpty()) return
    val colors = Pleos.colors
    val type = Pleos.type
    val minLat = stops.minOf { it.latitude }
    val maxLat = stops.maxOf { it.latitude }
    val minLon = stops.minOf { it.longitude }
    val maxLon = stops.maxOf { it.longitude }
    Column {
        BasicText("방문지 2D 위치 개요", style = type.titleSmall.copy(color = colors.textPrimary))
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val margin = 24.dp.toPx()
            val width = (size.width - margin * 2).coerceAtLeast(1f)
            val height = (size.height - margin * 2).coerceAtLeast(1f)
            val points = stops.map { stop ->
                val x = ((stop.longitude - minLon) / (maxLon - minLon).coerceAtLeast(0.000001)).toFloat()
                val y = ((stop.latitude - minLat) / (maxLat - minLat).coerceAtLeast(0.000001)).toFloat()
                Offset(margin + x * width, margin + (1f - y) * height)
            }
            points.zipWithNext().forEach { (a, b) -> drawLine(colors.active, a, b, strokeWidth = 3.dp.toPx()) }
            points.forEach { point ->
                drawCircle(colors.active, 9.dp.toPx(), point)
                drawCircle(colors.backgroundPrimary, 9.dp.toPx(), point, style = Stroke(2.dp.toPx()))
            }
        }
        BasicText(stops.joinToString(" → ") { "${it.label} ${it.name}" }, style = type.bodyMedium.copy(color = colors.textPrimary))
        BasicText("방문지 좌표를 직선으로 연결한 개요 · 실제 도로 경로·현재 위치·회전 안내 아님", style = type.labelMedium.copy(color = colors.textSecondary))
    }
}
