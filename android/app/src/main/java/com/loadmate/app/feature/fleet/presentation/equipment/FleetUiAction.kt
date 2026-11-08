package com.loadmate.app.feature.fleet.presentation.equipment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.loadmate.app.AppContainer
import com.loadmate.app.core.session.DriverStore
import com.loadmate.app.feature.fleet.domain.Driver
import com.loadmate.app.feature.fleet.domain.Equipment
import com.loadmate.app.feature.fleet.domain.FleetRepository
import com.loadmate.app.feature.fleet.domain.Handover
import com.loadmate.app.feature.fleet.domain.HandoverPreview
import com.loadmate.app.core.network.ApiResult
import com.loadmate.app.feature.trip.domain.TripRepository
import com.loadmate.app.core.network.describe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID


sealed interface FleetUiAction {
    data object Refresh : FleetUiAction
    data class SelectDriver(val id: String) : FleetUiAction
    data class SetEquipment(val id: String, val status: String) : FleetUiAction
    data class ToDriver(val id: String) : FleetUiAction
    data class Note(val text: String) : FleetUiAction
    data object CreateHandover : FleetUiAction
    data object DismissMessage : FleetUiAction
    data object AcceptHandover : FleetUiAction
}

/** S09 차량 장비 및 인계 (F56~F62). 인계 확정과 인수는 구분한다 (R13) */
