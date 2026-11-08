package com.loadmate.app.feature.trip.presentation.mismatch

import com.loadmate.app.feature.trip.presentation.shared.*
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.loadmate.app.feature.trip.domain.NewCargo
import kotlinx.coroutines.flow.update
import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.feature.trip.presentation.sitework.complete
import com.loadmate.app.feature.trip.presentation.sitework.submitPickup
import com.loadmate.app.AppContainer
import com.loadmate.app.feature.trip.presentation.shared.TripCoordinator
import com.loadmate.app.feature.trip.presentation.shared.TripUiAction
import com.loadmate.app.feature.trip.presentation.shared.TripUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

class MismatchViewModel(private val coordinator: TripCoordinator) : ViewModel() {
    val state: StateFlow<MismatchUiState> = coordinator.state.map { MismatchUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MismatchUiState())
    fun onAction(action: MismatchUiAction) {
        val a = action.toTripAction()
        when (a) {
            TripUiAction.RecheckAfterUnload -> coordinator.local.update { s ->
                s.copy(step = TripStep.SITE_WORK, pendingConfirmation = null, observationDraft = s.pendingConfirmation?.expected ?: s.observationDraft)
            }
            TripUiAction.DriverConfirmComplete -> coordinator.complete(true)
            is TripUiAction.OpenPickup -> coordinator.local.update { it.copy(pickupDraft = a.draft) }
            is TripUiAction.EditPickup -> coordinator.local.update { it.copy(pickupDraft = a.draft) }
            TripUiAction.DismissPickup -> coordinator.local.update { it.copy(pickupDraft = null) }
            TripUiAction.SubmitPickup -> coordinator.submitPickup()
            else -> coordinator.onAction(a)
        }
    }
    companion object {
        fun factory(container: AppContainer, tripId: String): ViewModelProvider.Factory = viewModelFactory { initializer { MismatchViewModel(container.tripCoordinator(tripId)) } }
    }
}
