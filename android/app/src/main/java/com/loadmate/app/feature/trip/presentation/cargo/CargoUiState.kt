package com.loadmate.app.feature.trip.presentation.cargo

import com.loadmate.app.core.pleos.RearDoorState
import com.loadmate.app.feature.records.domain.Memo
import com.loadmate.app.feature.trip.domain.Comparison
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.presentation.shared.PickupDraft
import com.loadmate.app.feature.trip.presentation.shared.TripUiState
import com.loadmate.app.feature.trip.presentation.shared.UiMessage
import java.time.Instant

data class CargoUiState(
    val loading: Boolean = true,
    val trip: Trip? = null,
    val loadError: String? = null,
    val offlineSince: Instant? = null,
    val message: UiMessage? = null,
    val lastComparison: Comparison? = null,
    val busy: Boolean = false,
    val rearDoor: RearDoorState = RearDoorState(),
    val doorWarningAt: Long? = null,
    val arrivedStopId: String? = null,
    val pickupDraft: PickupDraft? = null,
    val stopMemos: List<Memo> = emptyList(),
    val vehicleNotes: List<Memo> = emptyList(),
) {
    constructor(s: TripUiState) : this(s.loading, s.trip, s.loadError, s.offlineSince, s.message, s.lastComparison, s.busy, s.rearDoor, s.doorWarningAt, s.arrivedStopId, s.pickupDraft, s.stopMemos, s.vehicleNotes)
}
