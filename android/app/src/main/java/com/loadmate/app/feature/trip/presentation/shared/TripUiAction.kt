package com.loadmate.app.feature.trip.presentation.shared

sealed interface TripUiAction {
    data object Refresh : TripUiAction
    data object RefreshVehicleNotes : TripUiAction
    data object AskDeparture : TripUiAction
    data object DismissDeparture : TripUiAction
    data object ConfirmDeparture : TripUiAction
    data object AskRoute : TripUiAction
    data object ConfirmRoute : TripUiAction
    data object DismissRoute : TripUiAction
    // 하차 확인 팝업 (서버가 완료할 수 없다고 답했을 때만)
    data object DismissUnload : TripUiAction
    data object ConfirmUnload : TripUiAction
    data object PassUnloadMismatch : TripUiAction
    data object DismissUnloadMismatch : TripUiAction
    data object LoadStopNotes : TripUiAction
    data object DismissMessage : TripUiAction
    // 운행 관리 (F04·F18·F29·F07)
    data class CheckAddress(val address: String) : TripUiAction
    data object ClearAddressCheck : TripUiAction
    data class AddStop(val name: String, val address: String, val qty: Int) : TripUiAction
    data class EditStopPlace(val stopId: String, val name: String, val address: String) : TripUiAction
    data class ReorderStops(val stopIds: List<String>) : TripUiAction
    data class CancelStop(val stopId: String, val reason: String) : TripUiAction
    data class ChangeQty(val stopId: String, val cargoLineId: Int, val qty: Int) : TripUiAction
    data object AskEndTrip : TripUiAction
    data object DismissEndTrip : TripUiAction
    data class EndTrip(val reason: String?) : TripUiAction
    // 회수 (F44~F46)
    data class OpenPickup(val draft: PickupDraft = PickupDraft()) : TripUiAction
    data class EditPickup(val draft: PickupDraft) : TripUiAction
    data object SubmitPickup : TripUiAction
    data object DismissPickup : TripUiAction
    data class ReturnPickup(val pickupId: Int) : TripUiAction
    // 적재 이상 (F30·F31·F34)
    data class ResolveIssue(val issueId: Int, val resolution: String) : TripUiAction
    // 박스별 자리 (F12·F13)
    data class AcceptUnitPosition(val unitId: Int) : TripUiAction
    data class RestoreUnitPosition(val unitId: Int) : TripUiAction
}
