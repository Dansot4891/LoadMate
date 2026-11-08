package com.loadmate.app.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.loadmate.app.AppContainer
import com.loadmate.app.feature.settings.domain.ConnectionRepository
import com.loadmate.app.feature.settings.domain.ServerStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectionUiState(
    val baseUrl: String,
    val status: ServerStatus? = null,
    val isChecking: Boolean = false,
)

sealed interface ConnectionUiAction {
    data object Refresh : ConnectionUiAction
}

/** 단방향 흐름: UiAction → 상태 갱신 → StateFlow<UiState>. */
class ConnectionViewModel(
    private val repository: ConnectionRepository,
    baseUrl: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectionUiState(baseUrl = baseUrl))
    val state: StateFlow<ConnectionUiState> = _state.asStateFlow()

    init {
        onAction(ConnectionUiAction.Refresh)
    }

    fun onAction(action: ConnectionUiAction) {
        when (action) {
            ConnectionUiAction.Refresh -> refresh()
        }
    }

    private fun refresh() {
        if (_state.value.isChecking) return
        _state.update { it.copy(isChecking = true) }
        viewModelScope.launch {
            val result = repository.checkServer()
            _state.update { it.copy(status = result, isChecking = false) }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ConnectionViewModel(container.connectionRepository, container.apiBaseUrl) }
        }
    }
}
