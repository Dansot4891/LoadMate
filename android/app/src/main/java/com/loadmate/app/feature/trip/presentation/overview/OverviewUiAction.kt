package com.loadmate.app.feature.trip.presentation.overview

import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.feature.trip.domain.ObservationSource

sealed interface OverviewUiAction {
    data class CancelStop(val stopId: String, val reason: String) : OverviewUiAction
    data class ChangeQty(val stopId: String, val cargoLineId: Int, val qty: Int) : OverviewUiAction
    data class ReturnPickup(val pickupId: Int) : OverviewUiAction
    data class ResolveIssue(val issueId: Int, val resolution: String) : OverviewUiAction
    data class Common(val value: TripUiAction) : OverviewUiAction
    fun toTripAction(): TripUiAction = when (this) {
        is CancelStop -> TripUiAction.CancelStop(this.stopId, this.reason)
        is ChangeQty -> TripUiAction.ChangeQty(this.stopId, this.cargoLineId, this.qty)
        is ReturnPickup -> TripUiAction.ReturnPickup(this.pickupId)
        is ResolveIssue -> TripUiAction.ResolveIssue(this.issueId, this.resolution)
        is Common -> value
    }
    companion object {
        fun from(value: TripUiAction): OverviewUiAction = when (value) {
            is TripUiAction.CancelStop -> CancelStop(value.stopId, value.reason)
            is TripUiAction.ChangeQty -> ChangeQty(value.stopId, value.cargoLineId, value.qty)
            is TripUiAction.ReturnPickup -> ReturnPickup(value.pickupId)
            is TripUiAction.ResolveIssue -> ResolveIssue(value.issueId, value.resolution)
            else -> Common(value)
        }
    }
}
