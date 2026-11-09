package com.loadmate.app.feature.records.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.loadmate.app.AppContainer
import com.loadmate.app.core.network.ApiResult
import com.loadmate.app.core.network.describe
import com.loadmate.app.feature.trip.domain.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 이번 운행의 작업 변경 내역 (F64). 읽기 전용이며 최신이 위에 온다 */
class HistoryViewModel(private val trips: TripRepository, private val tripId: String) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    fun onAction(a: HistoryUiAction) {
        when (a) {
            HistoryUiAction.Refresh -> refresh()
            is HistoryUiAction.SelectFilter -> _state.update { it.copy(filter = a.filter) }
            HistoryUiAction.DismissMessage -> _state.update { it.copy(message = null) }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            when (val r = trips.history(tripId)) {
                is ApiResult.Success -> _state.update { it.copy(items = r.value.reversed()) }
                is ApiResult.Failure -> _state.update { it.copy(message = describe(r.error)) }
            }
        }
    }

    companion object {
        fun factory(c: AppContainer, tripId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoryViewModel(c.tripRepository, tripId) }
        }
    }
}
