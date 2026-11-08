package com.loadmate.app.feature.trip.presentation.sitework

import com.loadmate.app.feature.trip.presentation.shared.*
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.loadmate.app.feature.trip.domain.NewCargo
import kotlinx.coroutines.flow.update
import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.feature.trip.presentation.loadcheck.recordObservation
import com.loadmate.app.AppContainer
import com.loadmate.app.feature.trip.presentation.shared.TripCoordinator
import com.loadmate.app.feature.trip.presentation.shared.TripUiAction
import com.loadmate.app.feature.trip.presentation.shared.TripUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

class SiteWorkViewModel(private val coordinator: TripCoordinator) : ViewModel() {
    val state: StateFlow<SiteWorkUiState> = coordinator.state.map { SiteWorkUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SiteWorkUiState())
    fun onAction(action: SiteWorkUiAction) {
        val a = action.toTripAction()
        when (a) {
            is TripUiAction.DeliveredCount -> coordinator.local.update { it.copy(deliveredDraft = it.deliveredDraft + (a.kind to a.value)) }
            TripUiAction.RequestComplete -> coordinator.complete(false)
            TripUiAction.BackToDriving -> coordinator.local.update { it.copy(step = TripStep.DRIVING) }
            TripUiAction.SiteVisible -> coordinator.rearDoor.start()
            TripUiAction.SiteHidden -> coordinator.rearDoor.stop()
            is TripUiAction.ObservationCount -> coordinator.local.update { it.copy(observationDraft = it.observationDraft + (a.kind to a.value)) }
            is TripUiAction.ObservationSourceSelect -> coordinator.local.update { it.copy(observationSource = a.source) }
            TripUiAction.RecordObservation -> coordinator.recordObservation(false)
            TripUiAction.RecordUnavailable -> coordinator.recordObservation(true)
            is TripUiAction.OpenPickup -> coordinator.local.update { it.copy(pickupDraft = a.draft) }
            is TripUiAction.EditPickup -> coordinator.local.update { it.copy(pickupDraft = a.draft) }
            TripUiAction.DismissPickup -> coordinator.local.update { it.copy(pickupDraft = null) }
            TripUiAction.SubmitPickup -> coordinator.submitPickup()
            else -> coordinator.onAction(a)
        }
    }
    companion object {
        fun factory(container: AppContainer, tripId: String): ViewModelProvider.Factory = viewModelFactory { initializer { SiteWorkViewModel(container.tripCoordinator(tripId)) } }
    }
}
