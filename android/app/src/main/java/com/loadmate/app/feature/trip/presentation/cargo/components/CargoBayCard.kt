package com.loadmate.app.feature.trip.presentation.cargo.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.loadmate.app.feature.trip.domain.CargoUnit
import com.loadmate.app.feature.trip.domain.CargoUnitState
import com.loadmate.app.feature.trip.domain.Comparison
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.presentation.cargo.issueText
import com.loadmate.app.feature.trip.presentation.cargo.previewUnloadOrder
import com.loadmate.app.feature.trip.presentation.cargo.slotText
import com.loadmate.app.feature.trip.presentation.cargo.stopLegend
import com.loadmate.app.feature.trip.presentation.cargo.unitName
import com.loadmate.app.feature.trip.presentation.cargo.unloadOrder
import com.loadmate.app.feature.trip.presentation.cargo.unplacedCount
import com.loadmate.app.feature.trip.presentation.cargo.zoneCounts
import com.loadmate.app.feature.trip.presentation.shared.TripUiAction
import com.loadmate.app.feature.trip.presentation.shared.countsText
import com.loadmate.app.feature.trip.presentation.shared.zoneName
import com.loadmate.app.shared.designsystem.components.PleosButton
import com.loadmate.app.shared.designsystem.components.PleosButtonSize
import com.loadmate.app.shared.designsystem.components.PleosButtonStyle
import com.loadmate.app.shared.designsystem.components.PleosSegmentedControl
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.format.localTime
import com.loadmate.app.shared.designsystem.project.PleosCard
import com.loadmate.app.shared.designsystem.project.stopColor
import com.loadmate.app.shared.designsystem.theme.Pleos

private val VIEW_HEIGHT = 640.dp
private val DOT_SIZE = 14.dp
private val MODE_TAB_WIDTH = 260.dp
private enum class BayMode(val label: String) { THREE_D("3D"), TWO_D("2D") }

/** 적재 공간 3D·2D (F12·F13). 상차 기록 기준 자리이며 실제 위치를 보장하지 않는다 */
@Composable
internal fun CargoBayCard(trip: Trip, comparison: Comparison?, on: (TripUiAction) -> Unit, autoPlay: Long? = null, onAutoPlayed: () -> Unit = {}) {
    val c = Pleos.colors
    val t = Pleos.type
    var mode by remember { mutableStateOf(BayMode.THREE_D) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val hotZones = trip.openIssues.filter { it.unitId == null }.mapNotNull { it.zone }.toSet() +
        (if (comparison?.differences?.isNotEmpty() == true) trip.currentStop?.cargo?.mapNotNull { it.zone }.orEmpty() else emptyList())
    val issueUnits = trip.openIssues.mapNotNull { it.unitId }.toSet()
    val order = unloadOrder(trip, trip.currentStop)
    val highlight = order.map { it.id }.toSet()
    val shownOrder = previewUnloadOrder(trip, trip.currentStop)
    val preview = rememberUnloadPreview(shownOrder)
    // 도착 후 앱이 앞에 나오면 이번 방문지 꺼내는 순서를 한 번 자동 재생한다
    LaunchedEffect(autoPlay) {
        if (autoPlay == null) return@LaunchedEffect
        if (shownOrder.isNotEmpty()) preview.playing = true
        onAutoPlayed()
    }
    PleosCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
            BasicText("적재 공간", style = t.titleMedium.copy(color = c.textPrimary))
            Box(Modifier.weight(1f))
            if (shownOrder.isNotEmpty()) {
                PleosButton(if (preview.playing) "멈춤" else "재생", style = PleosButtonStyle.Basic) { preview.playing = !preview.playing }
            }
            PleosSegmentedControl(BayMode.entries, mode, { it.label }, Modifier.width(MODE_TAB_WIDTH)) { mode = it }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xl)) {
            stopLegend(trip).forEach { (owner, text) -> LegendRow(c.stopColor(owner), text) }
            Box(Modifier.weight(1f))
            trip.units.firstOrNull { it.id == selected }?.let { u -> BasicText(selectedText(trip, u), style = t.bodyLarge.copy(color = if (u.misplaced) c.negative else c.textSecondary)) }
        }
        trip.openIssues.forEach { i ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
                BasicText("${issueText(trip, i)} · ${localTime(i.observedAt)}", Modifier.weight(1f), style = t.bodyLarge.copy(color = c.negative))
                PleosButton("이상 없음", style = PleosButtonStyle.Basic, size = PleosButtonSize.Small) { on(TripUiAction.ResolveIssue(i.id, "no_issue")) }
            }
        }
        if (mode == BayMode.THREE_D) CargoBay3D(trip, hotZones, highlight, issueUnits, preview, selected, { selected = it }, Modifier.fillMaxWidth().height(VIEW_HEIGHT))
        else CargoBay2D(trip, hotZones, highlight, issueUnits, preview, selected, { selected = it }, Modifier.fillMaxWidth())
    }
    CargoSummaryCard(trip, comparison)
    MisplacedCard(trip, on)
}

private fun selectedText(trip: Trip, u: CargoUnit): String {
    val place = if (u.state == CargoUnitState.PLANNED) "계획 자리 ${slotText(u.plannedSlot)}" else slotText(u.slot)
    val plan = if (u.misplaced) " · 계획 ${slotText(u.plannedSlot)}" else ""
    return "${unitName(trip, u)} · $place$plan"
}

/** 배치도 아래 요약: 총 개수와 위치별 수 */
@Composable
private fun CargoSummaryCard(trip: Trip, comparison: Comparison?) {
    val c = Pleos.colors
    val t = Pleos.type
    val total = trip.units.count { it.state != CargoUnitState.UNLOADED }
    PleosCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xl)) {
            BasicText("총 개수: ${total}개", style = t.titleMedium.copy(color = c.textPrimary))
            zoneCounts(trip).forEach { (zone, count) ->
                BasicText("${zoneName(zone)} ${count}개", style = t.bodyLarge.copy(color = c.textSecondary))
            }
            if (unplacedCount(trip) > 0) BasicText("자리 미정 ${unplacedCount(trip)}개", style = t.bodyMedium.copy(color = c.negative))
            comparison?.takeIf { it.differences.isNotEmpty() }?.let {
                BasicText("예상 ${countsText(it.expected, trip)} · 확인 ${countsText(it.observed, trip)}", style = t.bodyMedium.copy(color = c.negative))
            }
        }
    }
}

@Composable
private fun LegendRow(color: androidx.compose.ui.graphics.Color, text: String) {
    val c = Pleos.colors
    val t = Pleos.type
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.s)) {
        Box(Modifier.size(DOT_SIZE).background(color, CircleShape))
        BasicText(text, style = t.bodyLarge.copy(color = c.textPrimary))
    }
}

/** 계획과 다른 자리에 기록된 물품. 잔량과 무관하며 기사가 정리한다 */
@Composable
private fun MisplacedCard(trip: Trip, on: (TripUiAction) -> Unit) {
    val odd = trip.units.filter { it.misplaced }
    if (odd.isEmpty()) return
    val c = Pleos.colors
    val t = Pleos.type
    PleosCard {
        BasicText("계획과 다른 자리 ${odd.size}개", style = t.titleSmall.copy(color = c.negative))
        odd.forEach { u ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
                BasicText("${unitName(trip, u)} · 계획 ${slotText(u.plannedSlot)} → 지금 ${slotText(u.slot)}", Modifier.weight(1f), style = t.bodyMedium.copy(color = c.textPrimary))
                if (!trip.isEnded) {
                    PleosButton("계획 자리로 옮겼어요", style = PleosButtonStyle.Basic, size = PleosButtonSize.Small) { on(TripUiAction.RestoreUnitPosition(u.id)) }
                    PleosButton("이 자리로 둘게요", style = PleosButtonStyle.Basic, size = PleosButtonSize.Small) { on(TripUiAction.AcceptUnitPosition(u.id)) }
                }
            }
        }
    }
}
