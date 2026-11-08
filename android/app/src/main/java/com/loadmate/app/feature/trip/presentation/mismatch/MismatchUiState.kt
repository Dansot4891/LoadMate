package com.loadmate.app.feature.trip.presentation.mismatch

import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.feature.trip.domain.*
import com.loadmate.app.core.pleos.RouteRequestStatus
import com.loadmate.app.core.pleos.RearDoorState
import com.loadmate.app.feature.records.domain.Memo
import java.time.Instant

data class MismatchUiState(
    val busy: Boolean = false,
    val deliveredDraft: Counts = emptyMap(),
    val pendingConfirmation: Comparison? = null,
) {
    constructor(s: TripUiState) : this(s.busy, s.deliveredDraft, s.pendingConfirmation)
}
