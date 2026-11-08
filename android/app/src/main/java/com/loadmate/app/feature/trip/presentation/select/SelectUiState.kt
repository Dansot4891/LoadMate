package com.loadmate.app.feature.trip.presentation.select

import com.loadmate.app.feature.trip.domain.TripSummary

data class SelectUiState(
    val loading: Boolean = true,
    val trips: List<TripSummary> = emptyList(),
    val error: String? = null,
)
