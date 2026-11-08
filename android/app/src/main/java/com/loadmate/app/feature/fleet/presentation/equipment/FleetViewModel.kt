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

class FleetViewModel(
    private val fleet: FleetRepository,
    private val trips: TripRepository,
    private val drivers: DriverStore,
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
            is FleetUiAction.SelectDriver -> {
                drivers.select(a.id, local.value.drivers.firstOrNull { it.id == a.id }?.name ?: a.id)
                local.update { it.copy(toDriverId = it.toDriverId?.takeUnless { target -> target == a.id }, message = null) }
                refresh()
            }
            is FleetUiAction.SetEquipment -> viewModelScope.launch {
                if (drivers.driverId.value == null) {
                    local.update { it.copy(message = "장비 상태를 기록할 기사를 먼저 선택하세요") }
                    return@launch
                }
                when (val r = fleet.setEquipmentStatus(a.id, a.status, null)) {
                    is ApiResult.Success -> { local.update { it.copy(message = "차량 공용 도구 상태를 기록했습니다") }; refresh() }
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
            if (trip != null) local.update { it.copy(tripDriverId = trip.driverId) }
            trip?.vehicleId?.let { v -> (fleet.equipment(v) as? ApiResult.Success)?.let { r -> local.update { it.copy(equipment = r.value) } } }
            (fleet.handoverPreview(tripId) as? ApiResult.Success)?.let { r -> local.update { it.copy(preview = r.value) } }
            (fleet.latestHandover(tripId) as? ApiResult.Success)?.let { r -> local.update { it.copy(handover = r.value) } }
        }
    }

    private fun create() {
        val s = state.value
        val to = s.toDriverId ?: return
        if (s.busy) return
        val actor = drivers.driverId.value
        if (actor == null || (s.tripDriverId != null && actor != s.tripDriverId) || to == actor) {
            local.update { it.copy(message = "운행 담당 기사로 선택하고, 다른 받는 기사를 지정하세요") }
            return
        }
        val key = handoverKey ?: UUID.randomUUID().toString().also { handoverKey = it }
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val r = fleet.createHandover(tripId, key, to, s.note)) {
                is ApiResult.Success -> { handoverKey = null; local.update { it.copy(busy = false, handover = r.value, message = "인계 요청을 보냈습니다. 받는 기사가 인수해야 운행 담당자가 바뀝니다") } }
                is ApiResult.Failure -> local.update { it.copy(busy = false, message = describe(r.error)) }
            }
        }
    }

    private fun accept() {
        val h = state.value.handover ?: return
        if (drivers.driverId.value != h.toDriverId) {
            local.update { it.copy(message = "받는 기사로 선택한 뒤 인수할 수 있습니다") }
            return
        }
        viewModelScope.launch {
            when (val r = fleet.acceptHandover(h.id)) {
                is ApiResult.Success -> { local.update { it.copy(handover = r.value, message = "인수했습니다. 이어서 운행하세요") }; refresh() }
                is ApiResult.Failure -> local.update { it.copy(message = describe(r.error)) }
            }
        }
    }

    companion object {
        fun factory(c: AppContainer, tripId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { FleetViewModel(c.fleetRepository, c.tripRepository, c.driverStore, tripId) }
        }
    }
}
