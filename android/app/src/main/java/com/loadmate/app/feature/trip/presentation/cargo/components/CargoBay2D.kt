package com.loadmate.app.feature.trip.presentation.cargo.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.loadmate.app.feature.trip.domain.CargoUnit
import com.loadmate.app.feature.trip.domain.CargoUnitState
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.presentation.cargo.CARGO_LANES
import com.loadmate.app.feature.trip.presentation.cargo.CARGO_ZONES
import com.loadmate.app.feature.trip.presentation.cargo.drawnSlot
import com.loadmate.app.feature.trip.presentation.cargo.laneName
import com.loadmate.app.feature.trip.presentation.shared.zoneName
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.project.stopColor
import com.loadmate.app.shared.designsystem.theme.Pleos

private val CELL_MIN_HEIGHT = 150.dp
private val CHIP_MIN_HEIGHT = 32.dp
private val LANE_LABEL_WIDTH = 64.dp

/** 위에서 본 배치. 왼쪽이 운전석, 오른쪽이 뒷문. 한 칸 안에 층별 상자를 아래층부터 위로 쌓아 보여 준다 */
@Composable
internal fun CargoBay2D(trip: Trip, hotZones: Set<String>, highlight: Set<Int>, issueUnits: Set<Int>, preview: UnloadPreviewState, selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier) {
    val c = Pleos.colors
    val t = Pleos.type
    val byCell = trip.units.filter { it.id !in preview.gone }.mapNotNull { u -> drawnSlot(u)?.let { it to u } }.groupBy({ (it.first.zone to it.first.lane) }, { it.first.layer to it.second })
    Column(modifier.clipToBounds(), verticalArrangement = Arrangement.spacedBy(PleosSpacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xs)) {
            BasicText("", Modifier.padding(end = LANE_LABEL_WIDTH))
            CARGO_ZONES.forEach { z ->
                BasicText(zoneName(z), Modifier.weight(1f), style = t.labelMedium.copy(color = if (z in hotZones) c.negative else c.textSecondary))
            }
        }
        (CARGO_LANES - 1 downTo 0).forEach { lane ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = PleosSpacing.xs)) { BasicText(laneName(lane), Modifier.defaultMinSize(minWidth = LANE_LABEL_WIDTH), style = t.labelMedium.copy(color = c.textTertiary)) }
                CARGO_ZONES.forEach { z ->
                    val hot = z in hotZones
                    Column(
                        Modifier.weight(1f).zIndex(if (byCell[z to lane].orEmpty().any { it.second.id == preview.activeId }) 1f else 0f).defaultMinSize(minHeight = CELL_MIN_HEIGHT)
                            .background(if (hot) c.surfaceAccent else c.surfaceHigh, RoundedCornerShape(PleosShape.small))
                            .border(PleosShape.hairline, if (hot) c.negative else c.divider, RoundedCornerShape(PleosShape.small))
                            .padding(PleosSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(PleosSpacing.xs, Alignment.Bottom),
                    ) {
                        byCell[z to lane].orEmpty().sortedByDescending { it.first }.forEach { (layer, u) ->
                            UnitChip(trip, u, layer, u.id in highlight, u.id in issueUnits, u.id == selected, if (u.id == preview.activeId) preview.progress else null, CARGO_ZONES.size - CARGO_ZONES.indexOf(z)) { onSelect(u.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitChip(trip: Trip, u: CargoUnit, layer: Int, highlighted: Boolean, issue: Boolean, selected: Boolean, leavingProgress: Float?, cellsToDoor: Int, onClick: () -> Unit) {
    val c = Pleos.colors
    val t = Pleos.type
    val owner = if (u.pickupId != null) null else trip.stops.firstOrNull { it.id == u.stopId }?.label
    val base = if (issue) c.negative else c.stopColor(owner)
    val planned = u.state == CargoUnitState.PLANNED && !issue
    val edge = when {
        u.misplaced -> c.negative
        selected || highlighted || leavingProgress != null -> c.textPrimary
        else -> base
    }
    Row(
        Modifier.fillMaxWidth()
            .graphicsLayer {
                if (leavingProgress != null) {
                    translationX = size.width * cellsToDoor * unloadSlideAmount(leavingProgress)
                    alpha = unloadAlpha(leavingProgress)
                    val grow = 1f + 0.05f * unloadLiftAmount(leavingProgress)
                    scaleX = grow
                    scaleY = grow
                }
            }
            .defaultMinSize(minHeight = CHIP_MIN_HEIGHT)
            .background(if (planned) base.copy(alpha = 0.16f) else base, RoundedCornerShape(PleosShape.small))
            .border(PleosShape.focusBorder.takeIf { selected || u.misplaced } ?: PleosShape.hairline, edge, RoundedCornerShape(PleosShape.small))
            .clickable(onClick = onClick)
            .padding(horizontal = PleosSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val color = if (planned) c.textSecondary else c.onStatic
        BasicText("${owner ?: "회"}${u.no}", style = t.titleSmall.copy(color = color))
        BasicText("${layer}층", style = t.labelMedium.copy(color = color))
    }
}
