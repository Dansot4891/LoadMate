package com.loadmate.app.feature.trip.presentation.loadcheck

import com.loadmate.app.feature.trip.presentation.shared.*
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.loadmate.app.feature.trip.domain.NewCargo
import kotlinx.coroutines.flow.update
import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.AppContainer
import com.loadmate.app.feature.trip.presentation.shared.TripCoordinator
import com.loadmate.app.feature.trip.presentation.shared.TripUiAction
import com.loadmate.app.feature.trip.presentation.shared.TripUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

class LoadCheckViewModel(private val coordinator: TripCoordinator) : ViewModel() {
    val state: StateFlow<LoadCheckUiState> = coordinator.state.map { LoadCheckUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadCheckUiState())
    fun onAction(action: LoadCheckUiAction) {
        val a = action.toTripAction()
        when (a) {
            is TripUiAction.ObservationCount -> coordinator.local.update { it.copy(observationDraft = it.observationDraft + (a.kind to a.value)) }
            is TripUiAction.ObservationSourceSelect -> coordinator.local.update { it.copy(observationSource = a.source) }
            TripUiAction.RecordObservation -> coordinator.recordObservation(false)
            TripUiAction.RecordUnavailable -> coordinator.recordObservation(true)
            TripUiAction.ConfirmBaseline -> coordinator.confirmBaseline()
            TripUiAction.BackToOverview -> coordinator.local.update { it.copy(step = TripStep.OVERVIEW) }
            else -> coordinator.onAction(a)
        }
    }
    companion object {
        fun factory(container: AppContainer, tripId: String): ViewModelProvider.Factory = viewModelFactory { initializer { LoadCheckViewModel(container.tripCoordinator(tripId)) } }
    }
}
