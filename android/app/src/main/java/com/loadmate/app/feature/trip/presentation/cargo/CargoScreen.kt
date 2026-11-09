package com.loadmate.app.feature.trip.presentation.cargo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loadmate.app.feature.trip.presentation.cargo.components.CargoBayCard
import com.loadmate.app.feature.trip.presentation.cargo.components.RearDoorStatusCard
import com.loadmate.app.feature.trip.presentation.shared.ArrivalNotesCard
import com.loadmate.app.feature.trip.presentation.shared.IssuesCard
import com.loadmate.app.feature.trip.presentation.shared.PickupDialog
import com.loadmate.app.feature.trip.presentation.shared.PickupsCard
import com.loadmate.app.feature.trip.presentation.shared.TripFrame
import com.loadmate.app.feature.trip.presentation.shared.TripUiAction
import com.loadmate.app.feature.trip.presentation.shared.ZoneView
import com.loadmate.app.shared.designsystem.components.PleosButton
import com.loadmate.app.shared.designsystem.components.PleosButtonStyle

/** 적재 현황: 박스별 3D·2D 배치, 회수품, 이상 의심 */
@Composable
fun CargoScreen(vm: CargoViewModel, autoPlay: Long? = null, onAutoPlayed: () -> Unit = {}) {
    val s by vm.state.collectAsStateWithLifecycle()
    val on: (TripUiAction) -> Unit = { vm.onAction(CargoUiAction.from(it)) }
    TripFrame(
        s.trip, s.loading, s.loadError, s.offlineSince, s.message, on,
        overlays = { s.pickupDraft?.let { PickupDialog(it, s.trip?.currentStop, s.busy, on) } },
    ) { trip ->
        val stopHere = trip.currentStop?.takeIf { it.id == s.arrivedStopId }
        LaunchedEffect(stopHere?.id) { if (stopHere != null) on(TripUiAction.LoadStopNotes) }
        if (stopHere != null) ArrivalNotesCard(s.stopMemos, s.vehicleNotes)
        RearDoorStatusCard(s.rearDoor, s.doorWarningAt)
        // 박스별 기록이 없는 운행은 구역 단위로만 보여 준다
        if (trip.units.isEmpty()) ZoneView(trip, s.lastComparison) else CargoBayCard(trip, s.lastComparison, on, autoPlay, onAutoPlayed)
        if (stopHere != null) PleosButton("회수 기록", style = PleosButtonStyle.Basic) { on(TripUiAction.OpenPickup()) }
        PickupsCard(trip, on)
        // 배치도가 있으면 이상은 배치도 카드 안에 표시한다
        if (trip.units.isEmpty()) IssuesCard(trip, on)
    }
}
