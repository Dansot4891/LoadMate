package com.loadmate.app.feature.records.presentation.memo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.loadmate.app.AppContainer
import com.loadmate.app.core.network.ApiResult
import com.loadmate.app.core.network.describe
import com.loadmate.app.feature.records.domain.Memo
import com.loadmate.app.feature.records.domain.MemoRepository
import com.loadmate.app.feature.records.domain.NewMemo
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.domain.TripRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** 차량 · 장소 · 이번 운행 메모 (F49~F54). 앱에서 쓰는 메모는 모두 공유다 */
class MemoViewModel(
    private val trips: TripRepository,
    private val memos: MemoRepository,
    private val tripId: String,
    private val newKey: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _state = MutableStateFlow(MemoUiState())
    val state: StateFlow<MemoUiState> = _state.asStateFlow()
    private var createKey: String? = null

    fun onAction(a: MemoUiAction) {
        when (a) {
            MemoUiAction.Refresh -> refresh()
            is MemoUiAction.SelectScope -> {
                _state.update { it.copy(scope = a.scope, query = "", searching = false) }
                viewModelScope.launch { load() }
            }
            is MemoUiAction.SelectFilter -> _state.update { it.copy(filter = a.filter) }
            is MemoUiAction.Query -> _state.update { it.copy(query = a.text) }
            MemoUiAction.Search -> {
                _state.update { it.copy(searching = it.query.isNotBlank()) }
                viewModelScope.launch { load() }
            }
            MemoUiAction.OpenEditor -> _state.update { s ->
                val target = if (s.scope?.isAll == true) s.draft.target ?: s.saveTargets.firstOrNull() else s.scope
                s.copy(editorOpen = true, draft = s.draft.copy(target = target, vehicle = target?.isVehicle == true))
            }
            is MemoUiAction.Draft -> _state.update { it.copy(draft = a.draft) }
            MemoUiAction.Save -> save()
            MemoUiAction.CancelEdit -> {
                createKey = null
                _state.update { it.copy(editorOpen = false, draft = MemoDraft()) }
            }
            MemoUiAction.DismissEditor -> _state.update {
                it.copy(editorOpen = false, draft = if (it.draft.editingId != null) MemoDraft() else it.draft)
            }
            is MemoUiAction.Edit -> _state.update {
                val m = a.memo
                it.copy(editorOpen = true, draft = MemoDraft(m.body, m.location.orEmpty(), kindOf(m.category), m.thisTripOnly, m.id, m.scopeType == "vehicle"))
            }
            is MemoUiAction.AskDelete -> _state.update { it.copy(deleteTarget = a.memo) }
            MemoUiAction.DismissDelete -> _state.update { it.copy(deleteTarget = null) }
            MemoUiAction.ConfirmDelete -> _state.value.deleteTarget?.let { target ->
                _state.update { it.copy(deleteTarget = null) }
                change("삭제했습니다") { memos.delete(target.id) }
            }
            MemoUiAction.DismissMessage -> _state.update { it.copy(message = null, messageIsError = false) }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val trip = when (val r = trips.getTrip(tripId)) {
                is ApiResult.Success -> r.value
                is ApiResult.Failure -> return@launch fail(describe(r.error))
            }
            val scopes = scopesOf(trip)
            _state.update { s ->
                // 도착하면 "현재 장소 A"가 "장소 A"로 바뀌므로 이름이 아니라 대상으로 다시 찾는다
                s.copy(scopes = scopes, scope = scopes.firstOrNull { it.type == s.scope?.type && it.id == s.scope.id } ?: scopes.firstOrNull())
            }
            load()
        }
    }

    private fun scopesOf(t: Trip): List<MemoScope> = buildList {
        add(MemoScope.ALL)
        t.vehicleId?.let { add(MemoScope("vehicle", it, "차량")) }
        t.currentStop?.let { add(MemoScope("place", it.placeKey, "현재 장소 ${it.label}")) }
        t.stops.filter { it.id != t.currentStop?.id }.forEach { add(MemoScope("place", it.placeKey, "장소 ${it.label}")) }
        add(MemoScope("trip", t.id, "이번 운행"))
    }

    private suspend fun load() {
        val s = _state.value
        val r = if (s.searching) memos.list(null, null, tripId, s.query) else {
            val scope = s.scope ?: return
            if (scope.isAll) listAll(s.scopes) else listScope(scope)
        }
        when (r) {
            is ApiResult.Success -> _state.update { it.copy(memos = r.value) }
            is ApiResult.Failure -> fail(describe(r.error))
        }
    }

    private suspend fun listScope(scope: MemoScope): ApiResult<List<Memo>> =
        memos.list(scope.type, scope.id, tripId.takeIf { scope.type == "place" }, null)

    /** 대상 없이 조회하면 다른 차량·운행의 메모까지 오므로 이 운행의 대상만 모은다. 하나라도 실패하면 일부만 보여 주지 않는다 */
    private suspend fun listAll(scopes: List<MemoScope>): ApiResult<List<Memo>> = coroutineScope {
        val results = scopes.filterNot { it.isAll }.map { async { listScope(it) } }.awaitAll()
        results.filterIsInstance<ApiResult.Failure>().firstOrNull()
            ?: ApiResult.Success(results.filterIsInstance<ApiResult.Success<List<Memo>>>().flatMap { it.value }.distinctBy { it.id }.sortedByDescending { it.updatedAt })
    }

    private fun fail(message: String) = _state.update { it.copy(busy = false, message = message, messageIsError = true) }

    private fun change(done: String, request: suspend () -> ApiResult<*>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val r = request()) {
                is ApiResult.Success -> {
                    load()
                    _state.update { it.copy(busy = false, message = done, messageIsError = false) }
                }
                is ApiResult.Failure -> fail(describe(r.error))
            }
        }
    }

    private fun save() {
        val s = _state.value
        val d = s.draft
        val target = d.target
        if (d.body.isBlank() || s.busy || (d.editingId == null && target == null)) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r: ApiResult<Memo> = if (d.editingId != null) {
                if (d.vehicle) memos.update(d.editingId, d.body, null, d.kind.category, d.location) else memos.update(d.editingId, d.body, null)
            } else {
                // 응답을 받지 못했을 때 다시 저장해도 중복되지 않도록 같은 키를 쓴다
                val key = createKey ?: newKey().also { createKey = it }
                memos.create(key, newMemo(target!!, d))
            }
            when (r) {
                is ApiResult.Success -> {
                    createKey = null
                    load()
                    _state.update { it.copy(busy = false, editorOpen = false, draft = MemoDraft(), message = "저장했습니다", messageIsError = false) }
                }
                is ApiResult.Failure -> fail(describe(r.error))
            }
        }
    }

    private fun newMemo(scope: MemoScope, d: MemoDraft): NewMemo =
        if (scope.isVehicle) NewMemo(scope.type, scope.id, d.body, shared = true, thisTripOnly = false, tripId = null, category = d.kind.category, location = d.location.ifBlank { null })
        else NewMemo(scope.type, scope.id, d.body, shared = true, thisTripOnly = d.thisTripOnly, tripId = tripId.takeIf { d.thisTripOnly })

    companion object {
        fun factory(c: AppContainer, tripId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { MemoViewModel(c.tripRepository, c.memoRepository, tripId) }
        }
    }
}
