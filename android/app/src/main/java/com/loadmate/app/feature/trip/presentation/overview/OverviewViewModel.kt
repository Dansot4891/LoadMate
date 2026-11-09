package com.loadmate.app.feature.trip.presentation.overview

import com.loadmate.app.feature.trip.presentation.shared.*
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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

class OverviewViewModel(private val coordinator: TripCoordinator) : ViewModel() {
    val state: StateFlow<OverviewUiState> = coordinator.state.map { OverviewUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())
    fun onAction(action: OverviewUiAction) {
        val a = action.toTripAction()
        when (a) {
            is TripUiAction.CancelStop -> coordinator.change("cancel:${a.stopId}", "경로에서 삭제했습니다") { t, k -> coordinator.repo.cancelStop(t.id, a.stopId, k, a.reason) }
            is TripUiAction.ChangeQty -> coordinator.change("qty:${a.cargoLineId}:${a.qty}", null) { t, k -> coordinator.repo.changeCargoQty(t.id, a.stopId, k, a.cargoLineId, a.qty) }
            is TripUiAction.ReturnPickup -> coordinator.change("return:${a.pickupId}", "반납했습니다") { t, k -> coordinator.repo.returnPickup(t.id, a.pickupId, k) }
            is TripUiAction.ResolveIssue -> coordinator.change("resolve:${a.issueId}", "확인했습니다") { t, k -> coordinator.repo.resolveIssue(t.id, a.issueId, k, a.resolution, null) }
            else -> coordinator.onAction(a)
        }
    }
    companion object {
        fun factory(container: AppContainer, tripId: String): ViewModelProvider.Factory = viewModelFactory { initializer { OverviewViewModel(container.tripCoordinator(tripId)) } }
    }
}
