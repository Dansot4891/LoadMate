package com.loadmate.app.feature.trip.presentation.mismatch

import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.feature.trip.domain.ObservationSource

sealed interface MismatchUiAction {
    data object RecheckAfterUnload : MismatchUiAction
    data object DriverConfirmComplete : MismatchUiAction
    data class OpenPickup(val draft: PickupDraft) : MismatchUiAction
    data class EditPickup(val draft: PickupDraft) : MismatchUiAction
    data object DismissPickup : MismatchUiAction
    data object SubmitPickup : MismatchUiAction
    data class Common(val value: TripUiAction) : MismatchUiAction
    fun toTripAction(): TripUiAction = when (this) {
        is RecheckAfterUnload -> TripUiAction.RecheckAfterUnload
        is DriverConfirmComplete -> TripUiAction.DriverConfirmComplete
        is OpenPickup -> TripUiAction.OpenPickup(this.draft)
        is EditPickup -> TripUiAction.EditPickup(this.draft)
        is DismissPickup -> TripUiAction.DismissPickup
        is SubmitPickup -> TripUiAction.SubmitPickup
        is Common -> value
    }
    companion object {
        fun from(value: TripUiAction): MismatchUiAction = when (value) {
            TripUiAction.RecheckAfterUnload -> RecheckAfterUnload
            TripUiAction.DriverConfirmComplete -> DriverConfirmComplete
            is TripUiAction.OpenPickup -> OpenPickup(value.draft)
            is TripUiAction.EditPickup -> EditPickup(value.draft)
            TripUiAction.DismissPickup -> DismissPickup
            TripUiAction.SubmitPickup -> SubmitPickup
            else -> Common(value)
        }
    }
}
