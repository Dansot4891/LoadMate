package com.loadmate.app.feature.trip.presentation.shared

import com.loadmate.app.feature.trip.domain.ObservationSource

sealed interface TripUiAction {
    data object Refresh : TripUiAction
    data object OpenLoadCheck : TripUiAction
    data object BackToOverview : TripUiAction
    data class ObservationCount(val kind: String, val value: Int) : TripUiAction
    data class ObservationSourceSelect(val source: ObservationSource) : TripUiAction
    data object RecordObservation : TripUiAction
    data object RecordUnavailable : TripUiAction
    data object ConfirmBaseline : TripUiAction
    data object AskRoute : TripUiAction
    data object ConfirmRoute : TripUiAction
    data object DismissRoute : TripUiAction
    data object Arrived : TripUiAction
    data object BackToDriving : TripUiAction
    data class DeliveredCount(val kind: String, val value: Int) : TripUiAction
    data object RequestComplete : TripUiAction
    data object RecheckAfterUnload : TripUiAction
    data object DriverConfirmComplete : TripUiAction
    data object DismissMessage : TripUiAction
    data object SiteVisible : TripUiAction
    data object SiteHidden : TripUiAction
    // 운행 관리 (F04·F18·F29·F07)
    data class AddSampleStop(val index: Int) : TripUiAction
    data class MoveStop(val stopId: String, val up: Boolean) : TripUiAction
    data class CancelStop(val stopId: String, val reason: String) : TripUiAction
    data class ChangeQty(val stopId: String, val cargoLineId: Int, val qty: Int) : TripUiAction
    data object AskEndTrip : TripUiAction
    data object DismissEndTrip : TripUiAction
    data class EndTrip(val reason: String?) : TripUiAction
    // 회수 (F44~F46). fromMismatch: S06 "회수품이에요" 분기
    data class OpenPickup(val draft: PickupDraft = PickupDraft()) : TripUiAction
    data class EditPickup(val draft: PickupDraft) : TripUiAction
    data object SubmitPickup : TripUiAction
    data object DismissPickup : TripUiAction
    data class ReturnPickup(val pickupId: Int) : TripUiAction
    // 적재 이상 (F30·F31·F34). 카메라가 없어 샘플 관측으로 기록
    data class RecordSampleIssue(val kind: String) : TripUiAction
    data class ResolveIssue(val issueId: Int, val resolution: String) : TripUiAction
}
