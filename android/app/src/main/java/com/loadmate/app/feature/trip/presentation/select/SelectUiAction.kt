package com.loadmate.app.feature.trip.presentation.select

sealed interface SelectUiAction {
    data object Refresh : SelectUiAction
    data class Choose(val tripId: String) : SelectUiAction
}
