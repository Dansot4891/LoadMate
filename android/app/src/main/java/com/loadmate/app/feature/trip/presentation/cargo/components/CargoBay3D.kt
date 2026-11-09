package com.loadmate.app.feature.trip.presentation.cargo.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.loadmate.app.feature.trip.domain.CargoUnit
import com.loadmate.app.feature.trip.domain.CargoUnitState
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.presentation.cargo.CARGO_LAYERS
import com.loadmate.app.feature.trip.presentation.cargo.CARGO_ZONES
import com.loadmate.app.R
import com.loadmate.app.shared.designsystem.components.PleosIconButton
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.feature.trip.presentation.cargo.drawnSlot
import com.loadmate.app.shared.designsystem.color.PleosColors
import com.loadmate.app.shared.designsystem.project.stopColor
import com.loadmate.app.shared.designsystem.theme.Pleos

private const val DROP_HEIGHT = 1.4f
private const val DROP_MS = 380

/** 앱에서 직접 그리는 3D 배치도. 끌어서 돌리고 눌러서 상자를 고른다 */
@Composable
internal fun CargoBay3D(trip: Trip, hotZones: Set<String>, highlight: Set<Int>, issueUnits: Set<Int>, preview: UnloadPreviewState, selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier) {
    val c = Pleos.colors
    val type = Pleos.type
    val measurer = rememberTextMeasurer()
    var yaw by remember { mutableFloatStateOf(DEFAULT_YAW) }
    var pitch by remember { mutableFloatStateOf(DEFAULT_PITCH) }
    var zoom by remember { mutableFloatStateOf(1f) }

    // 방금 실린 상자만 위에서 내려앉는다
    val loaded = trip.units.filter { it.state == CargoUnitState.LOADED }.map { it.id }.toSet()
    var known by remember { mutableStateOf(loaded) }
    var dropping by remember { mutableStateOf<Int?>(null) }
    val drop = remember { Animatable(1f) }
    LaunchedEffect(loaded) {
        val fresh = (loaded - known).lastOrNull()
        known = loaded
        if (fresh != null) {
            dropping = fresh
            drop.snapTo(0f)
            drop.animateTo(1f, tween(DROP_MS))
            dropping = null
        }
    }

    val boxes = trip.units.filter { it.id !in preview.gone }.mapNotNull { u ->
        drawnSlot(u)?.let { s ->
            val zoneIndex = CARGO_ZONES.indexOf(s.zone)
            val leaving = u.id == preview.activeId
            val lift = when {
                leaving -> unloadLiftAmount(preview.progress) * (CARGO_LAYERS - s.layer + 0.3f) * CELL_Z
                u.id == dropping -> (1f - drop.value) * DROP_HEIGHT
                else -> 0f
            }
            val slide = if (leaving) unloadSlideAmount(preview.progress) * (BAY_X - zoneIndex * CELL_X + CELL_X * 0.6f) else 0f
            u to BoxSpec(u.id, zoneIndex, s.lane, s.layer, lift, slide)
        }
    }
    val byId = boxes.associate { it.first.id to it.first }
    val latest = rememberUpdated(boxes.map { it.second }, onSelect, yaw, pitch, zoom)

    Box(modifier) {
        Canvas(
            Modifier.fillMaxSize()
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        yaw += pan.x * 0.35f
                        pitch = (pitch + pan.y * 0.25f).coerceIn(MIN_PITCH, MAX_PITCH)
                        zoom = clampZoom(zoom * gestureZoom)
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                zoom = clampZoom(zoom * (1f - event.changes.first().scrollDelta.y * WHEEL_ZOOM))
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val l = latest.value
                        val p = BayProjector(l.yaw, l.pitch, size.width.toFloat(), size.height.toFloat(), l.zoom)
                        l.onSelect(hitBox(visibleBoxFaces(l.boxes, p), P2(tap.x, tap.y)))
                    }
                },
        ) {
            val p = BayProjector(yaw, pitch, size.width, size.height, zoom)
            drawBayShell(p, c, hotZones, measurer, type.labelMedium)
            val faces = visibleBoxFaces(boxes.map { it.second }, p)
            faces.forEach { f ->
                val u = byId[f.boxId] ?: return@forEach
                drawBoxFace(f, u, trip, c, highlight, issueUnits, selected, u.id == preview.activeId, if (u.id == preview.activeId) unloadAlpha(preview.progress) else 1f, measurer, type.titleSmall)
            }
            drawNearRails(p, c)
        }
        Row(
            Modifier.align(Alignment.BottomEnd).background(c.surfaceHigh, RoundedCornerShape(PleosShape.small)).padding(PleosSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xs),
        ) {
            PleosIconButton(R.drawable.pleos_ic_minus, "축소") { zoom = clampZoom(zoom / ZOOM_STEP) }
            PleosIconButton(R.drawable.pleos_ic_plus, "확대") { zoom = clampZoom(zoom * ZOOM_STEP) }
        }
    }
}

private class Latest(val boxes: List<BoxSpec>, val onSelect: (Int?) -> Unit, val yaw: Float, val pitch: Float, val zoom: Float)

/** 포인터 입력 블록을 다시 만들지 않고 최신 값을 읽게 한다 */
@Composable
private fun rememberUpdated(boxes: List<BoxSpec>, onSelect: (Int?) -> Unit, yaw: Float, pitch: Float, zoom: Float) =
    remember { mutableStateOf(Latest(boxes, onSelect, yaw, pitch, zoom)) }.also { it.value = Latest(boxes, onSelect, yaw, pitch, zoom) }

private fun polygon(points: List<P2>) = Path().apply {
    moveTo(points[0].x, points[0].y)
    points.drop(1).forEach { lineTo(it.x, it.y) }
    close()
}

private fun DrawScope.drawBayShell(p: BayProjector, c: PleosColors, hot: Set<String>, measurer: TextMeasurer, label: TextStyle) {
    // 안쪽이 보이는 벽 (바깥을 향한 면이 카메라 반대쪽인 벽)
    boxOutlineFaces(p, 0f, BAY_X, 0f, BAY_Y, 0f, BAY_Z).filter { it.first != FaceKind.TOP && !facesCamera(it.second) }.forEach { (_, face) ->
        val path = polygon(face)
        drawPath(path, c.surfaceLow.copy(alpha = 0.55f))
        drawPath(path, c.divider, style = Stroke(2f))
    }
    CARGO_ZONES.forEachIndexed { i, zone ->
        val x0 = i * CELL_X
        val x1 = x0 + CELL_X
        val path = polygon(p.face(Triple(x0, 0f, 0f), Triple(x1, 0f, 0f), Triple(x1, BAY_Y, 0f), Triple(x0, BAY_Y, 0f)))
        drawPath(path, if (zone in hot) c.negative.copy(alpha = 0.35f) else c.surfaceHigh)
        drawPath(path, if (zone in hot) c.negative else c.dividerPrimary, style = Stroke(if (zone in hot) 4f else 2f))
        for (lane in 1 until 3) {
            val a = p.project(x0, lane * CELL_Y, 0f)
            val b = p.project(x1, lane * CELL_Y, 0f)
            drawLine(c.divider, Offset(a.x, a.y), Offset(b.x, b.y), 1.5f)
        }
        centerLabel(measurer, ZONE_LABELS[i], p.project(x0 + CELL_X / 2, -0.4f, 0f), label.copy(color = if (zone in hot) c.negative else c.textSecondary))
    }
    centerLabel(measurer, "운전석", p.project(0f, BAY_Y / 2, BAY_Z + 0.3f), label.copy(color = c.textTertiary))
    centerLabel(measurer, "뒷문", p.project(BAY_X + 0.5f, BAY_Y / 2, 0f), label.copy(color = c.textTertiary))
}

private const val WHEEL_ZOOM = 0.1f

private val ZONE_LABELS = listOf("앞쪽", "가운데", "뒷문 쪽")

/** 카메라 쪽 벽은 면을 칠하지 않고 윗선만 그려 안이 보이게 한다 */
private fun DrawScope.drawNearRails(p: BayProjector, c: PleosColors) {
    val top = listOf(P2(0f, 0f), P2(BAY_X, 0f), P2(BAY_X, BAY_Y), P2(0f, BAY_Y))
    top.indices.forEach { i ->
        val a = top[i]
        val b = top[(i + 1) % top.size]
        val mid = p.depth((a.x + b.x) / 2, (a.y + b.y) / 2, BAY_Z)
        if (mid < 0f) {
            val s = p.project(a.x, a.y, BAY_Z)
            val e = p.project(b.x, b.y, BAY_Z)
            drawLine(c.dividerPrimary, Offset(s.x, s.y), Offset(e.x, e.y), 2f)
            val sb = p.project(a.x, a.y, 0f)
            drawLine(c.divider, Offset(s.x, s.y), Offset(sb.x, sb.y), 1.5f)
            val eb = p.project(b.x, b.y, 0f)
            drawLine(c.divider, Offset(e.x, e.y), Offset(eb.x, eb.y), 1.5f)
        }
    }
}

private fun DrawScope.drawBoxFace(f: BoxFace, u: CargoUnit, trip: Trip, c: PleosColors, highlight: Set<Int>, issueUnits: Set<Int>, selected: Int?, leaving: Boolean, alpha: Float, measurer: TextMeasurer, label: TextStyle) {
    val owner = if (u.pickupId != null) null else trip.stops.firstOrNull { it.id == u.stopId }?.label
    val issue = u.id in issueUnits
    val base = if (issue) c.negative else c.stopColor(owner)
    val planned = u.state == CargoUnitState.PLANNED && !issue
    val fill = when (f.kind) {
        FaceKind.TOP -> base
        FaceKind.SIDE_X -> lerp(base, Color.Black, 0.22f)
        FaceKind.SIDE_Y -> lerp(base, Color.Black, 0.36f)
    }
    val path = polygon(f.points)
    drawPath(path, if (planned) fill.copy(alpha = 0.16f) else fill, alpha = alpha)
    val (edge, width) = when {
        u.misplaced -> c.negative to 5f
        u.id == selected || leaving -> c.textPrimary to 5f
        u.id in highlight -> c.textPrimary to 3f
        planned -> base.copy(alpha = 0.7f) to 2f
        else -> lerp(base, Color.Black, 0.5f) to 1.5f
    }
    drawPath(path, edge, alpha = alpha, style = Stroke(width))
    if (f.kind == FaceKind.TOP && !planned) {
        centerLabel(measurer, "${owner ?: "회"}${u.no}", centerOf(f.points), label.copy(color = c.onStatic.copy(alpha = alpha)))
    }
}

private fun DrawScope.centerLabel(measurer: TextMeasurer, text: String, at: P2, style: TextStyle) {
    val layout = measurer.measure(text, style)
    val x = at.x - layout.size.width / 2
    val y = at.y - layout.size.height / 2
    if (x < 0f || y < 0f || x + layout.size.width > size.width || y + layout.size.height > size.height) return
    drawText(layout, topLeft = Offset(x, y))
}
