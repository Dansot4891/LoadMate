package com.loadmate.app.feature.trip.presentation.loadcheck

import com.loadmate.app.feature.trip.presentation.TripUiState

/** 이 화면이 표시하는 운행 UI 값. 서버 운행 자체는 Repository StateFlow가 소유한다. */
data class LoadCheckUiState(val content: TripUiState = TripUiState())
