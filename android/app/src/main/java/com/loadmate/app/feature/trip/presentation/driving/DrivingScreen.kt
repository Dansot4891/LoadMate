package com.loadmate.app.feature.trip.presentation.driving

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.presentation.driving.components.NaviCard
import com.loadmate.app.feature.trip.presentation.shared.RoutePlan
import com.loadmate.app.feature.trip.presentation.shared.StopDialog
import com.loadmate.app.feature.trip.presentation.shared.StopListCard
import com.loadmate.app.feature.trip.presentation.shared.TripUiAction
import com.loadmate.app.feature.trip.presentation.shared.RouteLoadGapCard
import com.loadmate.app.shared.designsystem.components.PleosButtonStyle
import com.loadmate.app.shared.designsystem.components.PleosPopupAction
import com.loadmate.app.shared.designsystem.components.PleosSystemPopup
import com.loadmate.app.shared.designsystem.project.PleosCard
import com.loadmate.app.shared.designsystem.theme.Pleos

@Composable
internal fun DrivingScreen(s: DrivingUiState, trip: Trip, on: (TripUiAction) -> Unit, onDialog: (StopDialog) -> Unit) {
    val c = Pleos.colors
    val t = Pleos.type
    val stop = trip.currentStop
    if (stop != null) {
        NaviCard(s, stop, on)
    } else {
        PleosCard {
            BasicText("방문지를 모두 마쳤습니다", style = t.titleMedium.copy(color = c.textPrimary))
            BasicText("적재 메뉴에서 회수품을 반납해 주세요.", style = t.bodyMedium.copy(color = c.textSecondary))
        }
    }
    RouteLoadGapCard(trip)
    StopListCard(trip, on, onDialog)
}

@Composable
internal fun RouteDialog(plan: RoutePlan, waiting: String?, gap: String?, on: (TripUiAction) -> Unit) {
    val c = Pleos.colors
    val t = Pleos.type
    PleosSystemPopup(
        title = "안내를 시작할까요?",
        onDismiss = { on(TripUiAction.DismissRoute) },
        actions = listOf(
            PleosPopupAction("시작") { on(TripUiAction.ConfirmRoute) },
            PleosPopupAction("취소", PleosButtonStyle.Basic) { on(TripUiAction.DismissRoute) },
        ),
    ) {
        BasicText(plan.stopLabels.joinToString(" → "), style = t.titleSmall.copy(color = c.textPrimary))
        BasicText((plan.waypoints + plan.destination).joinToString("\n") { it.name }, style = t.bodyMedium.copy(color = c.textSecondary))
        gap?.let { BasicText("짐이 달라요 · $it", style = t.bodyMedium.copy(color = c.negative)) }
        waiting?.let { BasicText(it, style = t.bodyMedium.copy(color = c.textSecondary)) }
        BasicText("진행 중인 안내는 새 경로로 바뀝니다.", style = t.labelMedium.copy(color = c.textTertiary))
    }
}
