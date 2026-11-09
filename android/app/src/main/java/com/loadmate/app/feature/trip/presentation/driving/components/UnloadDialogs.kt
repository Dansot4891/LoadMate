package com.loadmate.app.feature.trip.presentation.driving.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import com.loadmate.app.feature.trip.presentation.driving.DrivingUiState
import com.loadmate.app.feature.trip.presentation.shared.TripUiAction
import com.loadmate.app.feature.trip.presentation.shared.countsText
import com.loadmate.app.feature.trip.presentation.shared.unloadDifferenceText
import com.loadmate.app.shared.designsystem.components.PleosButtonStyle
import com.loadmate.app.shared.designsystem.components.PleosPopupAction
import com.loadmate.app.shared.designsystem.components.PleosSystemPopup
import com.loadmate.app.shared.designsystem.theme.Pleos

/** 하차 확인 팝업. 수량을 입력받지 않고, 다르면 차이만 보여 주고 기사가 넘어갈 수 있게 한다 */
@Composable
internal fun UnloadDialogs(s: DrivingUiState, on: (TripUiAction) -> Unit) {
    val trip = s.trip ?: return
    val stop = trip.currentStop ?: return
    val c = Pleos.colors
    val t = Pleos.type
    val comparison = s.pendingConfirmation
    when {
        comparison != null -> PleosSystemPopup(
            title = "${stop.label} 수량이 달라요. 확인해 주세요.",
            onDismiss = { on(TripUiAction.DismissUnloadMismatch) },
            actions = listOf(
                PleosPopupAction("넘어가기", enabled = !s.busy) { on(TripUiAction.PassUnloadMismatch) },
                PleosPopupAction("다시 확인할게요", PleosButtonStyle.Basic) { on(TripUiAction.DismissUnloadMismatch) },
            ),
        ) {
            comparison.differences.forEach { d ->
                BasicText(unloadDifferenceText(d, trip.kindNames[d.kind] ?: d.kind), style = t.bodyLarge.copy(color = c.negative))
            }
            BasicText("일부러 남겼거나 잘못 인식했다면 넘어가세요.", style = t.bodyMedium.copy(color = c.textSecondary))
        }
        s.unloadAskStopId == stop.id -> PleosSystemPopup(
            title = "${stop.label} 하차 끝났나요?",
            onDismiss = { on(TripUiAction.DismissUnload) },
            actions = listOf(
                PleosPopupAction("맞아요", enabled = !s.busy) { on(TripUiAction.ConfirmUnload) },
                PleosPopupAction("아직이에요", PleosButtonStyle.Basic) { on(TripUiAction.DismissUnload) },
            ),
        ) {
            BasicText("${stop.name} · ${countsText(stop.plannedCounts, trip)}", style = t.titleSmall.copy(color = c.textPrimary))
        }
    }
}
