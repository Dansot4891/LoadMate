package com.loadmate.app.feature.trip.presentation.overview

import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.feature.trip.domain.ObservationSource

sealed interface OverviewUiAction {
    data object OpenLoadCheck : OverviewUiAction
    data class AddSampleStop(val index: Int) : OverviewUiAction
    data class MoveStop(val stopId: String, val up: Boolean) : OverviewUiAction
    data class CancelStop(val stopId: String, val reason: String) : OverviewUiAction
    data class ChangeQty(val stopId: String, val cargoLineId: Int, val qty: Int) : OverviewUiAction
    data class ReturnPickup(val pickupId: Int) : OverviewUiAction
    data class RecordSampleIssue(val kind: String) : OverviewUiAction
    data class ResolveIssue(val issueId: Int, val resolution: String) : OverviewUiAction
    data class Common(val value: TripUiAction) : OverviewUiAction
    fun toTripAction(): TripUiAction = when (this) {
        is OpenLoadCheck -> TripUiAction.OpenLoadCheck
        is AddSampleStop -> TripUiAction.AddSampleStop(this.index)
        is MoveStop -> TripUiAction.MoveStop(this.stopId, this.up)
        is CancelStop -> TripUiAction.CancelStop(this.stopId, this.reason)
        is ChangeQty -> TripUiAction.ChangeQty(this.stopId, this.cargoLineId, this.qty)
        is ReturnPickup -> TripUiAction.ReturnPickup(this.pickupId)
        is RecordSampleIssue -> TripUiAction.RecordSampleIssue(this.kind)
        is ResolveIssue -> TripUiAction.ResolveIssue(this.issueId, this.resolution)
        is Common -> value
    }
    companion object {
        fun from(value: TripUiAction): OverviewUiAction = when (value) {
            TripUiAction.OpenLoadCheck -> OpenLoadCheck
            is TripUiAction.AddSampleStop -> AddSampleStop(value.index)
            is TripUiAction.MoveStop -> MoveStop(value.stopId, value.up)
            is TripUiAction.CancelStop -> CancelStop(value.stopId, value.reason)
            is TripUiAction.ChangeQty -> ChangeQty(value.stopId, value.cargoLineId, value.qty)
            is TripUiAction.ReturnPickup -> ReturnPickup(value.pickupId)
            is TripUiAction.RecordSampleIssue -> RecordSampleIssue(value.kind)
            is TripUiAction.ResolveIssue -> ResolveIssue(value.issueId, value.resolution)
            else -> Common(value)
        }
    }
}
