package com.loadmate.app.feature.trip.presentation.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loadmate.app.feature.trip.presentation.driving.DrivingScreen
import com.loadmate.app.feature.trip.presentation.driving.DrivingUiAction
import com.loadmate.app.feature.trip.presentation.driving.DrivingUiState
import com.loadmate.app.feature.trip.presentation.driving.DrivingViewModel
import com.loadmate.app.feature.trip.presentation.driving.RouteDialog
import com.loadmate.app.feature.trip.presentation.overview.OverviewScreen
import com.loadmate.app.feature.trip.presentation.overview.OverviewUiAction
import com.loadmate.app.feature.trip.presentation.overview.OverviewUiState
import com.loadmate.app.feature.trip.presentation.overview.OverviewViewModel
import com.loadmate.app.feature.trip.presentation.overview.components.DepartureDialog

@Composable
fun TripScreen(
    overview: OverviewViewModel,
    driving: DrivingViewModel,
) {
    val state by overview.state.collectAsStateWithLifecycle()
    val drivingState by driving.state.collectAsStateWithLifecycle()
    val action: (TripUiAction) -> Unit = { value ->
        when (state.step) {
            TripStep.OVERVIEW, TripStep.DONE -> overview.onAction(OverviewUiAction.from(value))
            TripStep.DRIVING -> driving.onAction(DrivingUiAction.from(value))
        }
    }
    TripContent(state, drivingState, action)
}

@Composable
private fun TripContent(
    s: OverviewUiState,
    driving: DrivingUiState,
    on: (TripUiAction) -> Unit,
) {
    var stopDialog by remember { mutableStateOf<StopDialog?>(null) }
    val openDialog: (StopDialog) -> Unit = { stopDialog = it }
    TripFrame(
        trip = s.trip,
        loading = s.loading,
        loadError = s.loadError,
        offlineSince = s.offlineSince,
        message = s.message,
        on = on,
        overlays = {
            if (s.departureDialog) s.trip?.let { DepartureDialog(it, on) }
            s.routeDialog?.let { RouteDialog(it, s.trip?.let { t -> routeWaitingText(t.pendingStops) }, s.trip?.let { t -> routeLoadGapText(t) }, on) }
            if (s.endDialog) s.trip?.let { EndTripDialog(it, on) }
            s.trip?.let { trip -> stopDialog?.let { StopDialogHost(it, trip, s.addressCheck, on) { stopDialog = null } } }
        },
    ) { trip ->
        when (s.step) {
            TripStep.OVERVIEW -> OverviewScreen(trip, s.vehicleNotes, on, openDialog)
            TripStep.DRIVING -> DrivingScreen(driving, trip, on, openDialog)
            TripStep.DONE -> {
                DonePanel(trip)
                StopListCard(trip, on, openDialog)
            }
        }
    }
}
