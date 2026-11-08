package com.loadmate.app.feature.trip.presentation.sitework

import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.feature.trip.domain.ObservationSource

sealed interface SiteWorkUiAction {
    data class DeliveredCount(val kind: String, val value: Int) : SiteWorkUiAction
    data object RequestComplete : SiteWorkUiAction
    data object BackToDriving : SiteWorkUiAction
    data object SiteVisible : SiteWorkUiAction
    data object SiteHidden : SiteWorkUiAction
    data class ObservationCount(val kind: String, val value: Int) : SiteWorkUiAction
    data class ObservationSourceSelect(val source: ObservationSource) : SiteWorkUiAction
    data object RecordObservation : SiteWorkUiAction
    data object RecordUnavailable : SiteWorkUiAction
    data class OpenPickup(val draft: PickupDraft) : SiteWorkUiAction
    data class EditPickup(val draft: PickupDraft) : SiteWorkUiAction
    data object DismissPickup : SiteWorkUiAction
    data object SubmitPickup : SiteWorkUiAction
    data class Common(val value: TripUiAction) : SiteWorkUiAction
    fun toTripAction(): TripUiAction = when (this) {
        is DeliveredCount -> TripUiAction.DeliveredCount(this.kind, this.value)
        is RequestComplete -> TripUiAction.RequestComplete
        is BackToDriving -> TripUiAction.BackToDriving
        is SiteVisible -> TripUiAction.SiteVisible
        is SiteHidden -> TripUiAction.SiteHidden
        is ObservationCount -> TripUiAction.ObservationCount(this.kind, this.value)
        is ObservationSourceSelect -> TripUiAction.ObservationSourceSelect(this.source)
        is RecordObservation -> TripUiAction.RecordObservation
        is RecordUnavailable -> TripUiAction.RecordUnavailable
        is OpenPickup -> TripUiAction.OpenPickup(this.draft)
        is EditPickup -> TripUiAction.EditPickup(this.draft)
        is DismissPickup -> TripUiAction.DismissPickup
        is SubmitPickup -> TripUiAction.SubmitPickup
        is Common -> value
    }
    companion object {
        fun from(value: TripUiAction): SiteWorkUiAction = when (value) {
            is TripUiAction.DeliveredCount -> DeliveredCount(value.kind, value.value)
            TripUiAction.RequestComplete -> RequestComplete
            TripUiAction.BackToDriving -> BackToDriving
            TripUiAction.SiteVisible -> SiteVisible
            TripUiAction.SiteHidden -> SiteHidden
            is TripUiAction.ObservationCount -> ObservationCount(value.kind, value.value)
            is TripUiAction.ObservationSourceSelect -> ObservationSourceSelect(value.source)
            TripUiAction.RecordObservation -> RecordObservation
            TripUiAction.RecordUnavailable -> RecordUnavailable
            is TripUiAction.OpenPickup -> OpenPickup(value.draft)
            is TripUiAction.EditPickup -> EditPickup(value.draft)
            TripUiAction.DismissPickup -> DismissPickup
            TripUiAction.SubmitPickup -> SubmitPickup
            else -> Common(value)
        }
    }
}
