package com.loadmate.app.feature.trip.presentation.driving.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import java.util.Locale
import com.loadmate.app.core.pleos.RouteRequestStatus
import com.loadmate.app.feature.trip.domain.Stop
import com.loadmate.app.feature.trip.presentation.driving.DrivingUiState
import com.loadmate.app.feature.trip.presentation.shared.TripUiAction
import com.loadmate.app.feature.trip.presentation.shared.routeWaitingText
import com.loadmate.app.shared.designsystem.components.PleosButton
import com.loadmate.app.shared.designsystem.components.PleosButtonStyle
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.project.PleosCard
import com.loadmate.app.shared.designsystem.project.PleosTag
import com.loadmate.app.shared.designsystem.theme.Pleos

/** 내비 안내 전달과 결과. 실패하면 여기서 알아채고 다시 보낼 수 있어야 한다 */
@Composable
internal fun NaviCard(s: DrivingUiState, stop: Stop, on: (TripUiAction) -> Unit) {
    val c = Pleos.colors
    val t = Pleos.type
    val (label, tone) = when (val r = s.routeStatus) {
        // 앱 재실행 시 이전 요청 결과는 알 수 없다 (내비의 현재 안내 조회는 NAVI_ROUTE 권한 필요, 미연결)
        RouteRequestStatus.None -> "" to c.textSecondary
        RouteRequestStatus.Waiting -> "안내 준비 중" to c.textSecondary
        is RouteRequestStatus.Started -> "안내 중 · ${r.name} · ${String.format(Locale.ROOT, "%.1f", r.distanceMeters / 1000.0)}km · ${r.durationSeconds / 60}분" to c.positive
        is RouteRequestStatus.Failed -> "안내 실패" to c.negative
        RouteRequestStatus.Cancelled -> "안내 취소됨" to c.regulationOrange
    }
    val retry = s.routeStatus is RouteRequestStatus.Failed || s.routeStatus == RouteRequestStatus.Cancelled
    val sentForThisStop = s.lastRoutePlan?.stopLabels?.firstOrNull() == stop.label && !retry
    PleosCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xl)) {
            PleosButton(
                "안내 시작",
                style = if (sentForThisStop) PleosButtonStyle.Basic else PleosButtonStyle.Filled,
            ) { on(TripUiAction.AskRoute) }
            Column(verticalArrangement = Arrangement.spacedBy(PleosSpacing.xs)) {
                if (label.isNotEmpty()) BasicText(label, style = t.bodyLarge.copy(color = tone))
                s.lastRoutePlan?.let { BasicText("경로 ${it.stopLabels.joinToString(" → ")}", style = t.labelMedium.copy(color = c.textSecondary)) }
                s.trip?.let { routeWaitingText(it.pendingStops) }?.let { BasicText(it, style = t.labelMedium.copy(color = c.textSecondary)) }
            }
            if (!s.naviConnected) PleosTag("내비 연결 중")
        }
    }
}
