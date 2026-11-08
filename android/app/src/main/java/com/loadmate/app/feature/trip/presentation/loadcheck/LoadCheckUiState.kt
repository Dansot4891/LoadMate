package com.loadmate.app.feature.trip.presentation.loadcheck

import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.feature.trip.domain.*
import com.loadmate.app.core.pleos.RouteRequestStatus
import com.loadmate.app.core.pleos.RearDoorState
import com.loadmate.app.feature.records.domain.Memo
import java.time.Instant

data class LoadCheckUiState(
    override val busy: Boolean = false,
    override val observationDraft: Counts = emptyMap(),
    override val observationSource: ObservationSource = ObservationSource.SAMPLE,
    override val lastComparison: Comparison? = null,
) : ObservationUiState {
    constructor(s: TripUiState) : this(s.busy, s.observationDraft, s.observationSource, s.lastComparison)
}
