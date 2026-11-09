package com.loadmate.app.feature.records.presentation.history

sealed interface HistoryUiAction {
    data object Refresh : HistoryUiAction
    data class SelectFilter(val filter: HistoryFilter) : HistoryUiAction
    data object DismissMessage : HistoryUiAction
}
