package com.loadmate.app.feature.trip.presentation.driving

import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.feature.trip.domain.*
import com.loadmate.app.core.pleos.RouteRequestStatus
import com.loadmate.app.core.pleos.RearDoorState
import com.loadmate.app.feature.records.domain.Memo
import java.time.Instant

data class DrivingUiState(
    val naviConnected: Boolean = false,
    val routeStatus: RouteRequestStatus = RouteRequestStatus.None,
    val lastRoutePlan: RoutePlan? = null,
    val trip: Trip? = null,
    val busy: Boolean = false,
    val arrivedStopId: String? = null,
    val unloadAskStopId: String? = null,
    val pendingConfirmation: Comparison? = null,
) {
    constructor(s: TripUiState) : this(s.naviConnected, s.routeStatus, s.lastRoutePlan, s.trip, s.busy, s.arrivedStopId, s.unloadAskStopId, s.pendingConfirmation)
}
