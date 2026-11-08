package com.loadmate.app.feature.trip.presentation.overview

import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.feature.trip.domain.*
import com.loadmate.app.core.pleos.RouteRequestStatus
import com.loadmate.app.core.pleos.RearDoorState
import com.loadmate.app.feature.records.domain.Memo
import java.time.Instant

data class OverviewUiState(
    val loading: Boolean = true,
    val trip: Trip? = null,
    val loadError: String? = null,
    val offlineSince: Instant? = null,
    val step: TripStep = TripStep.OVERVIEW,
    val busy: Boolean = false,
    val message: UiMessage? = null,
    val routeDialog: RoutePlan? = null,
    val pickupDraft: PickupDraft? = null,
    val endDialog: Boolean = false,
) {
    constructor(s: TripUiState) : this(s.loading, s.trip, s.loadError, s.offlineSince, s.step, s.busy, s.message, s.routeDialog, s.pickupDraft, s.endDialog)
}
