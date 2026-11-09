package com.loadmate.app.feature.trip.presentation.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import com.loadmate.app.feature.records.domain.Memo
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.presentation.shared.RouteLoadGapCard
import com.loadmate.app.feature.trip.presentation.shared.VehicleCheckCard
import com.loadmate.app.feature.trip.presentation.shared.StopDialog
import com.loadmate.app.feature.trip.presentation.shared.StopListCard
import com.loadmate.app.feature.trip.presentation.shared.TripUiAction
import com.loadmate.app.feature.trip.presentation.shared.countsText
import com.loadmate.app.shared.designsystem.components.PleosButton
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.project.PleosCard
import com.loadmate.app.shared.designsystem.theme.Pleos

/** 출발 전: 내비 안내를 누르면 적재 확인 팝업을 한 번 거친다. 수량·배치는 적재 메뉴에서 본다 */
@Composable
internal fun OverviewScreen(trip: Trip, vehicleNotes: List<Memo>, on: (TripUiAction) -> Unit, onDialog: (StopDialog) -> Unit) {
    val c = Pleos.colors
    val t = Pleos.type
    LaunchedEffect(trip.id) { on(TripUiAction.RefreshVehicleNotes) }
    VehicleCheckCard(vehicleNotes)
    PleosCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xl)) {
            trip.currentStop?.let { stop -> PleosButton("안내 시작") { on(TripUiAction.AskDeparture) } }
            Column(verticalArrangement = Arrangement.spacedBy(PleosSpacing.xs)) {
                BasicText("방문지 ${trip.stops.size}곳 · 배송품 ${countsText(trip.plannedTotals, trip)}", style = t.bodyLarge.copy(color = c.textPrimary))
            }
        }
    }
    RouteLoadGapCard(trip)
    StopListCard(trip, on, onDialog)
}
