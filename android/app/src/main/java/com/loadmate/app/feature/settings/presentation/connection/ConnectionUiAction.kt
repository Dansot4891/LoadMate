package com.loadmate.app.feature.settings.presentation.connection

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


sealed interface ConnectionUiAction {
    data object Refresh : ConnectionUiAction
}

/** 단방향 흐름: UiAction → 상태 갱신 → StateFlow<UiState>. */
