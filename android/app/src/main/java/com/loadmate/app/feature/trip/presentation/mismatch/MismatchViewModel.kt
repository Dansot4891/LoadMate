package com.loadmate.app.feature.trip.presentation.mismatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.loadmate.app.AppContainer
import com.loadmate.app.feature.trip.presentation.TripCoordinator
import com.loadmate.app.feature.trip.presentation.TripUiAction
import com.loadmate.app.feature.trip.presentation.TripUiState
import kotlinx.coroutines.flow.StateFlow

class MismatchViewModel(private val coordinator: TripCoordinator) : ViewModel() {
    val state: StateFlow<TripUiState> = coordinator.state
    fun onAction(action: TripUiAction) = coordinator.onAction(action)
    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory { initializer { MismatchViewModel(container.tripCoordinator) } }
    }
}
