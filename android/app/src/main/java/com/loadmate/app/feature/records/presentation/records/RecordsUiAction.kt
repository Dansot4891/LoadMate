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

sealed interface RecordsUiAction {
    data object Refresh : RecordsUiAction
    data class SelectScope(val scope: MemoScope) : RecordsUiAction
    data class Query(val text: String) : RecordsUiAction
    data object Search : RecordsUiAction
    data class Draft(val draft: MemoDraft) : RecordsUiAction
    data object Save : RecordsUiAction
    data class Edit(val memo: Memo) : RecordsUiAction
}

/** S07 기록(이력) · S08 현장 및 개인 메모. 개인 메모는 작성자만 본다 (R12) */
