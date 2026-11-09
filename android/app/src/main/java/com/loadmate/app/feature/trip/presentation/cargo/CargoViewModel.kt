package com.loadmate.app.feature.trip.presentation.cargo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.loadmate.app.AppContainer
import com.loadmate.app.feature.trip.presentation.shared.TripCoordinator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class CargoViewModel(private val coordinator: TripCoordinator) : ViewModel() {
    val state: StateFlow<CargoUiState> = coordinator.state.map { CargoUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CargoUiState())

    fun onAction(action: CargoUiAction) = coordinator.onAction(action.toTripAction())

    companion object {
        fun factory(container: AppContainer, tripId: String): ViewModelProvider.Factory =
            viewModelFactory { initializer { CargoViewModel(container.tripCoordinator(tripId)) } }
    }
}
