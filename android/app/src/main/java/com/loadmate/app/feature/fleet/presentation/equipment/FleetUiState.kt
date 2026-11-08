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
import com.loadmate.app.feature.trip.presentation.describe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID


data class FleetUiState(
    val drivers: List<Driver> = emptyList(),
    val driverId: String? = null,
    val tripDriverId: String? = null,
    val equipment: List<Equipment> = emptyList(),
    val preview: HandoverPreview? = null,
    val handover: Handover? = null,
    val toDriverId: String? = null,
    val note: String = "",
    val busy: Boolean = false,
    val message: String? = null,
)

