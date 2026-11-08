package com.loadmate.app.feature.trip.presentation.driving

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.loadmate.app.AppContainer
import com.loadmate.app.feature.trip.presentation.TripCoordinator
import com.loadmate.app.feature.trip.presentation.TripUiAction
import com.loadmate.app.feature.trip.presentation.TripUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

class DrivingViewModel(private val coordinator: TripCoordinator) : ViewModel() {
    val state: StateFlow<DrivingUiState> = coordinator.state.map { DrivingUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrivingUiState())
    fun onAction(action: DrivingUiAction) = coordinator.onAction(action.value)
    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory { initializer { DrivingViewModel(container.tripCoordinator) } }
    }
}
