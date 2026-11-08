package com.loadmate.app.feature.fleet.presentation

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

sealed interface FleetUiAction {
    data object Refresh : FleetUiAction
    data class SelectDriver(val id: String) : FleetUiAction
    data class SetEquipment(val id: String, val status: String) : FleetUiAction
    data class ToDriver(val id: String) : FleetUiAction
    data class Note(val text: String) : FleetUiAction
    data object CreateHandover : FleetUiAction
    data object AcceptHandover : FleetUiAction
}

/** S09 차량 장비 및 인계 (F56~F62). 인계 확정과 인수는 구분한다 (R13) */
class FleetViewModel(
    private val fleet: FleetRepository,
    private val trips: TripRepository,
    private val drivers: DriverStore,
    private val onTripChanged: () -> Unit,
    private val tripId: String,
) : ViewModel() {
    private val local = MutableStateFlow(FleetUiState())
    val state: StateFlow<FleetUiState> = combine(local, drivers.driverId) { s, d -> s.copy(driverId = d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FleetUiState())
    private var handoverKey: String? = null

    init { refresh() }

    fun onAction(a: FleetUiAction) {
        when (a) {
            FleetUiAction.Refresh -> refresh()
            is FleetUiAction.SelectDriver -> { drivers.select(a.id, local.value.drivers.firstOrNull { it.id == a.id }?.name ?: a.id); refresh() }
            is FleetUiAction.SetEquipment -> viewModelScope.launch {
                when (val r = fleet.setEquipmentStatus(a.id, a.status, null)) {
                    is ApiResult.Success -> refresh()
                    is ApiResult.Failure -> local.update { it.copy(message = describe(r.error)) }
                }
            }
            is FleetUiAction.ToDriver -> local.update { it.copy(toDriverId = a.id) }
            is FleetUiAction.Note -> local.update { it.copy(note = a.text) }
            FleetUiAction.CreateHandover -> create()
            FleetUiAction.AcceptHandover -> accept()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            (fleet.drivers() as? ApiResult.Success)?.let { r -> local.update { it.copy(drivers = r.value) } }
            val trip = (trips.getTrip(tripId) as? ApiResult.Success)?.value
            local.update { it.copy(tripDriverId = trip?.driverId) }
            trip?.vehicleId?.let { v -> (fleet.equipment(v) as? ApiResult.Success)?.let { r -> local.update { it.copy(equipment = r.value) } } }
            (fleet.handoverPreview(tripId) as? ApiResult.Success)?.let { r -> local.update { it.copy(preview = r.value) } }
            (fleet.latestHandover(tripId) as? ApiResult.Success)?.let { r -> local.update { it.copy(handover = r.value) } }
        }
    }

    private fun create() {
        val s = state.value
        val to = s.toDriverId ?: return
        if (s.busy) return
        val key = handoverKey ?: UUID.randomUUID().toString().also { handoverKey = it }
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val r = fleet.createHandover(tripId, key, to, s.note)) {
                is ApiResult.Success -> { handoverKey = null; local.update { it.copy(busy = false, handover = r.value, message = "인계를 확정했습니다. 인수 기사가 인수 확정해야 넘어갑니다") } }
                is ApiResult.Failure -> local.update { it.copy(busy = false, message = describe(r.error)) }
            }
        }
    }

    private fun accept() {
        val h = state.value.handover ?: return
        viewModelScope.launch {
            when (val r = fleet.acceptHandover(h.id)) {
                is ApiResult.Success -> { local.update { it.copy(handover = r.value, message = "인수했습니다. 이어서 운행하세요") }; onTripChanged(); refresh() }
                is ApiResult.Failure -> local.update { it.copy(message = describe(r.error)) }
            }
        }
    }

    companion object {
        fun factory(c: AppContainer, tripId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { FleetViewModel(c.fleetRepository, c.tripRepository, c.driverStore, c::notifyTripChanged, tripId) }
        }
    }
}
