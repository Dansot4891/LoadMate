package com.loadmate.app.feature.trip.presentation.driving

import com.loadmate.app.feature.trip.presentation.shared.*
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.loadmate.app.feature.trip.domain.NewCargo
import kotlinx.coroutines.flow.update
import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.feature.trip.presentation.overview.recordSampleIssue
import com.loadmate.app.AppContainer
import com.loadmate.app.feature.trip.presentation.shared.TripCoordinator
import com.loadmate.app.feature.trip.presentation.shared.TripUiAction
import com.loadmate.app.feature.trip.presentation.shared.TripUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

class DrivingViewModel(private val coordinator: TripCoordinator) : ViewModel() {
    val state: StateFlow<DrivingUiState> = coordinator.state.map { DrivingUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrivingUiState())
    fun onAction(action: DrivingUiAction) {
        val a = action.toTripAction()
        when (a) {
            TripUiAction.AskRoute -> coordinator.local.update { s -> s.copy(routeDialog = s.trip?.let { routePlan(it.pendingStops) }) }
            TripUiAction.ConfirmRoute -> coordinator.sendRoute()
            TripUiAction.DismissRoute -> coordinator.local.update { it.copy(routeDialog = null) }
            TripUiAction.Arrived -> coordinator.enterSite()
            TripUiAction.AskEndTrip -> coordinator.local.update { it.copy(endDialog = true) }
            TripUiAction.DismissEndTrip -> coordinator.local.update { it.copy(endDialog = false) }
            is TripUiAction.EndTrip -> {
                coordinator.local.update { it.copy(endDialog = false) }
                coordinator.change("end", "운행을 종료했습니다") { t, k -> coordinator.repo.endTrip(t.id, k, a.reason) }
            }
            is TripUiAction.RecordSampleIssue -> coordinator.recordSampleIssue(a.kind)
            else -> coordinator.onAction(a)
        }
    }
    companion object {
        fun factory(container: AppContainer, tripId: String): ViewModelProvider.Factory = viewModelFactory { initializer { DrivingViewModel(container.tripCoordinator(tripId)) } }
    }
}
