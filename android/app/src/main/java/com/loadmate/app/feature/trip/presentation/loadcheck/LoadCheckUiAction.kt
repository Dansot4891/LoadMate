package com.loadmate.app.feature.trip.presentation.loadcheck

import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.feature.trip.domain.ObservationSource

sealed interface LoadCheckUiAction {
    data class ObservationCount(val kind: String, val value: Int) : LoadCheckUiAction
    data class ObservationSourceSelect(val source: ObservationSource) : LoadCheckUiAction
    data object RecordObservation : LoadCheckUiAction
    data object RecordUnavailable : LoadCheckUiAction
    data object ConfirmBaseline : LoadCheckUiAction
    data object BackToOverview : LoadCheckUiAction
    data class Common(val value: TripUiAction) : LoadCheckUiAction
    fun toTripAction(): TripUiAction = when (this) {
        is ObservationCount -> TripUiAction.ObservationCount(this.kind, this.value)
        is ObservationSourceSelect -> TripUiAction.ObservationSourceSelect(this.source)
        is RecordObservation -> TripUiAction.RecordObservation
        is RecordUnavailable -> TripUiAction.RecordUnavailable
        is ConfirmBaseline -> TripUiAction.ConfirmBaseline
        is BackToOverview -> TripUiAction.BackToOverview
        is Common -> value
    }
    companion object {
        fun from(value: TripUiAction): LoadCheckUiAction = when (value) {
            is TripUiAction.ObservationCount -> ObservationCount(value.kind, value.value)
            is TripUiAction.ObservationSourceSelect -> ObservationSourceSelect(value.source)
            TripUiAction.RecordObservation -> RecordObservation
            TripUiAction.RecordUnavailable -> RecordUnavailable
            TripUiAction.ConfirmBaseline -> ConfirmBaseline
            TripUiAction.BackToOverview -> BackToOverview
            else -> Common(value)
        }
    }
}
