package com.loadmate.app.feature.trip.presentation.overview

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

class OverviewViewModel(private val coordinator: TripCoordinator) : ViewModel() {
    val state: StateFlow<OverviewUiState> = coordinator.state.map { OverviewUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())
    fun onAction(action: OverviewUiAction) {
        val a = action.toTripAction()
        when (a) {
            TripUiAction.OpenLoadCheck -> coordinator.local.update { it.copy(step = TripStep.LOAD_CHECK, observationDraft = it.trip?.plannedTotals.orEmpty()) }
            is TripUiAction.AddSampleStop -> SAMPLE_PLACES.getOrNull(a.index)?.let { p ->
                coordinator.change("addStop:${p.name}", "방문지 추가 · ${p.name}") { t, k -> coordinator.repo.addStop(t.id, k, p.name, p.address, p.lat, p.lng, listOf(NewCargo("박스", "box", 1))) }
            }
            is TripUiAction.MoveStop -> coordinator.moveStop(a.stopId, a.up)
            is TripUiAction.CancelStop -> coordinator.change("cancel:${a.stopId}", "방문지 취소 · 물품은 차에 남습니다") { t, k -> coordinator.repo.cancelStop(t.id, a.stopId, k, a.reason) }
            is TripUiAction.ChangeQty -> coordinator.change("qty:${a.cargoLineId}:${a.qty}", null) { t, k -> coordinator.repo.changeCargoQty(t.id, a.stopId, k, a.cargoLineId, a.qty) }
            is TripUiAction.ReturnPickup -> coordinator.change("return:${a.pickupId}", "반납 처리됨") { t, k -> coordinator.repo.returnPickup(t.id, a.pickupId, k) }
            is TripUiAction.RecordSampleIssue -> coordinator.recordSampleIssue(a.kind)
            is TripUiAction.ResolveIssue -> coordinator.change("resolve:${a.issueId}", "이상 처리 기록됨") { t, k -> coordinator.repo.resolveIssue(t.id, a.issueId, k, a.resolution, null) }
            else -> coordinator.onAction(a)
        }
    }
    companion object {
        fun factory(container: AppContainer, tripId: String): ViewModelProvider.Factory = viewModelFactory { initializer { OverviewViewModel(container.tripCoordinator(tripId)) } }
    }
}
