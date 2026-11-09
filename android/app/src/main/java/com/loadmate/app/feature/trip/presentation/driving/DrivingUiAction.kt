package com.loadmate.app.feature.trip.presentation.driving

import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.feature.trip.domain.ObservationSource

sealed interface DrivingUiAction {
    data object AskRoute : DrivingUiAction
    data object ConfirmRoute : DrivingUiAction
    data object DismissRoute : DrivingUiAction
    data object AskEndTrip : DrivingUiAction
    data object DismissEndTrip : DrivingUiAction
    data class EndTrip(val reason: String?) : DrivingUiAction
    data class Common(val value: TripUiAction) : DrivingUiAction
    fun toTripAction(): TripUiAction = when (this) {
        is AskRoute -> TripUiAction.AskRoute
        is ConfirmRoute -> TripUiAction.ConfirmRoute
        is DismissRoute -> TripUiAction.DismissRoute
        is AskEndTrip -> TripUiAction.AskEndTrip
        is DismissEndTrip -> TripUiAction.DismissEndTrip
        is EndTrip -> TripUiAction.EndTrip(this.reason)
        is Common -> value
    }
    companion object {
        fun from(value: TripUiAction): DrivingUiAction = when (value) {
            TripUiAction.AskRoute -> AskRoute
            TripUiAction.ConfirmRoute -> ConfirmRoute
            TripUiAction.DismissRoute -> DismissRoute
            TripUiAction.AskEndTrip -> AskEndTrip
            TripUiAction.DismissEndTrip -> DismissEndTrip
            is TripUiAction.EndTrip -> EndTrip(value.reason)
            else -> Common(value)
        }
    }
}
