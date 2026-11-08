package com.loadmate.app.feature.trip.presentation.select

sealed interface SelectUiEffect {
    data class OpenTrip(val tripId: String) : SelectUiEffect
}
