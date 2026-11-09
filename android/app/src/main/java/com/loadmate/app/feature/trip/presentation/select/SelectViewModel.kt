package com.loadmate.app.feature.trip.presentation.select

import com.loadmate.app.feature.trip.presentation.shared.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.loadmate.app.AppContainer
import com.loadmate.app.core.network.ApiError
import com.loadmate.app.core.network.ApiResult
import com.loadmate.app.feature.trip.domain.TripRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SelectViewModel(private val repository: TripRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(SelectUiState())
    val state = mutableState.asStateFlow()
    private val mutableEffect = MutableSharedFlow<SelectUiEffect>()
    val effect = mutableEffect.asSharedFlow()

    fun onAction(action: SelectUiAction) {
        when (action) {
            SelectUiAction.Refresh -> refresh()
            is SelectUiAction.Choose -> {
                if (state.value.trips.any { it.id == action.tripId }) {
                    viewModelScope.launch { mutableEffect.emit(SelectUiEffect.OpenTrip(action.tripId)) }
                }
            }
        }
    }

    private fun refresh() {
        if (state.value.loading && state.value.error == null && state.value.trips.isNotEmpty()) return
        mutableState.value = state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            mutableState.value = when (val result = repository.listTrips()) {
                is ApiResult.Success -> SelectUiState(loading = false, trips = result.value)
                is ApiResult.Failure -> state.value.copy(loading = false, error = errorText(result.error))
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SelectViewModel(container.tripRepository) }
        }
    }
}

internal fun errorText(error: ApiError): String = when (error) {
    ApiError.NotConfigured -> "서버 미설정"
    is ApiError.Network -> "서버에 연결할 수 없습니다"
    is ApiError.Http -> "운행 목록을 불러오지 못했습니다"
}
