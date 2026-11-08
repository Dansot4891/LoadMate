package com.loadmate.app.feature.records.presentation.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.loadmate.app.AppContainer
import com.loadmate.app.feature.records.domain.Memo
import com.loadmate.app.feature.records.domain.MemoRepository
import com.loadmate.app.feature.records.domain.NewMemo
import com.loadmate.app.core.network.ApiResult
import com.loadmate.app.feature.trip.domain.HistoryItem
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.domain.TripRepository
import com.loadmate.app.feature.trip.presentation.describe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** 메모 범위 선택 (F52). place 는 현재(또는 선택한) 방문지의 장소 */
class RecordsViewModel(
    private val trips: TripRepository,
    private val memos: MemoRepository,
    private val driverId: () -> String?,
    private val tripId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(RecordsUiState())
    val state: StateFlow<RecordsUiState> = _state.asStateFlow()
    private var createKey: String? = null

    init { refresh() }

    fun onAction(a: RecordsUiAction) {
        when (a) {
            RecordsUiAction.Refresh -> refresh()
            is RecordsUiAction.SelectScope -> { _state.update { it.copy(scope = a.scope, query = "") }; loadMemos() }
            is RecordsUiAction.Query -> _state.update { it.copy(query = a.text) }
            RecordsUiAction.Search -> loadMemos()
            is RecordsUiAction.Draft -> _state.update { it.copy(draft = a.draft) }
            RecordsUiAction.Save -> save()
            is RecordsUiAction.Edit -> _state.update { it.copy(draft = MemoDraft(a.memo.body, a.memo.shared, a.memo.thisTripOnly, a.memo.id)) }
        }
    }

    private fun refresh() {
        _state.update { it.copy(driverId = driverId()) }
        viewModelScope.launch {
            val t = (trips.getTrip(tripId) as? ApiResult.Success)?.value ?: run {
                _state.update { it.copy(message = "운행 정보를 불러오지 못했습니다") }
                return@launch
            }
            val scopes = buildList {
                t.currentStop?.let { add(MemoScope("place", it.placeKey, "현재 장소 ${it.label}")) }
                t.stops.filter { it.id != t.currentStop?.id }.forEach { add(MemoScope("place", it.placeKey, "장소 ${it.label}")) }
                t.vehicleId?.let { add(MemoScope("vehicle", it, "차량")) }
                add(MemoScope("trip", t.id, "이번 운행"))
            }
            _state.update { s -> s.copy(trip = t, scopes = scopes, scope = s.scope?.takeIf { it in scopes } ?: scopes.firstOrNull()) }
            (trips.history(tripId) as? ApiResult.Success)?.let { r -> _state.update { it.copy(history = r.value.reversed()) } }
            loadMemos()
        }
    }

    private fun loadMemos() {
        val s = _state.value
        val scope = s.scope
        viewModelScope.launch {
            val r = if (s.query.isNotBlank()) memos.list(null, null, tripId, s.query)
            else memos.list(scope?.type, scope?.id, tripId.takeIf { scope?.type != "trip" }, null)
            when (r) {
                is ApiResult.Success -> _state.update { it.copy(memos = r.value) }
                is ApiResult.Failure -> _state.update { it.copy(message = describe(r.error)) }
            }
        }
    }

    private fun save() {
        val s = _state.value
        val scope = s.scope ?: return
        val d = s.draft
        if (d.body.isBlank() || s.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = if (d.editingId != null) memos.update(d.editingId, d.body, null)
            else {
                val key = createKey ?: UUID.randomUUID().toString().also { createKey = it }
                memos.create(key, NewMemo(scope.type, scope.id, d.body, d.shared, d.thisTripOnly, tripId.takeIf { d.thisTripOnly }))
            }
            when (r) {
                is ApiResult.Success -> {
                    createKey = null
                    _state.update { it.copy(busy = false, draft = MemoDraft(), message = "저장했습니다") }
                    loadMemos()
                }
                is ApiResult.Failure -> _state.update { it.copy(busy = false, message = describe(r.error)) }
            }
        }
    }

    companion object {
        fun factory(c: AppContainer, tripId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecordsViewModel(c.tripRepository, c.memoRepository, { c.driverStore.driverId.value }, tripId) }
        }
    }
}
