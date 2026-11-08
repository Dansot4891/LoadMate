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

data class MemoScope(val type: String, val id: String, val label: String)

data class MemoDraft(val body: String = "", val shared: Boolean = false, val thisTripOnly: Boolean = false, val editingId: Int? = null)

data class RecordsUiState(
    val trip: Trip? = null,
    val history: List<HistoryItem> = emptyList(),
    val scopes: List<MemoScope> = emptyList(),
    val scope: MemoScope? = null,
    val query: String = "",
    val memos: List<Memo> = emptyList(),
    val draft: MemoDraft = MemoDraft(),
    val driverId: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

