package com.loadmate.app.feature.trip.presentation.cargo

import com.loadmate.app.feature.trip.presentation.shared.TripUiAction

sealed interface CargoUiAction {
    data class ReturnPickup(val pickupId: Int) : CargoUiAction
    data class ResolveIssue(val issueId: Int, val resolution: String) : CargoUiAction
    data class Common(val value: TripUiAction) : CargoUiAction

    fun toTripAction(): TripUiAction = when (this) {
        is ReturnPickup -> TripUiAction.ReturnPickup(pickupId)
        is ResolveIssue -> TripUiAction.ResolveIssue(issueId, resolution)
        is Common -> value
    }

    companion object {
        fun from(value: TripUiAction): CargoUiAction = when (value) {
            is TripUiAction.ReturnPickup -> ReturnPickup(value.pickupId)
            is TripUiAction.ResolveIssue -> ResolveIssue(value.issueId, value.resolution)
            else -> Common(value)
        }
    }
}
