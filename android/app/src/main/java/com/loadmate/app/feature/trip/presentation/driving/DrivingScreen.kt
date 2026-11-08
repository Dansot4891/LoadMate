package com.loadmate.app.feature.trip.presentation.driving

import com.loadmate.app.shared.designsystem.components.PleosSystemPopup
import com.loadmate.app.shared.designsystem.components.PleosPopupAction
import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.feature.trip.presentation.*

import com.loadmate.app.core.pleos.RouteRequestStatus
import com.loadmate.app.core.pleos.routeRequestStatus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loadmate.app.shared.designsystem.theme.Pleos
import com.loadmate.app.shared.designsystem.components.PleosButton
import com.loadmate.app.shared.designsystem.components.PleosButtonStyle
import com.loadmate.app.shared.designsystem.project.PleosCard
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.components.PleosStepper
import com.loadmate.app.shared.designsystem.project.PleosTag
import com.loadmate.app.core.pleos.RearDoorConnection
import com.loadmate.app.feature.trip.domain.Comparison
import com.loadmate.app.feature.trip.domain.ComparisonState
import com.loadmate.app.feature.trip.domain.ObservationSource
import com.loadmate.app.feature.trip.domain.Stop
import com.loadmate.app.feature.trip.domain.StopStatus
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.presentation.driving.components.RouteOverview

@Composable
internal fun DrivingScreen(s: DrivingUiState, trip: Trip, on: (TripUiAction) -> Unit) {
    val c = Pleos.colors
    val t = Pleos.type
    val stop = trip.currentStop ?: run {
        PleosCard {
            BasicText("방문지 처리 완료", style = t.titleMedium.copy(color = c.textPrimary))
            BasicText("반납할 회수품이 남아 있습니다. 반납 후 운행을 종료하세요.", style = t.bodyMedium.copy(color = c.textSecondary))
        }
        PickupsCard(trip, on)
        IssuesCard(trip, on, showSampleButtons = false)
        PleosButton("운행 종료", style = PleosButtonStyle.Basic) { on(TripUiAction.AskEndTrip) }
        return
    }
    PleosCard {
        BasicText("다음 방문지", style = t.labelMedium.copy(color = c.textTertiary))
        BasicText("${stop.label} · ${stop.name}", style = t.titleMedium.copy(color = c.textPrimary))
        BasicText("하차 예정 ${countsText(stop.plannedCounts, trip)}", style = t.bodyLarge.copy(color = c.textPrimary))
    }
    PleosCard { RouteOverview(trip.pendingStops) }
    PleosCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
            BasicText("내비 안내", style = t.titleSmall.copy(color = c.textPrimary))
            if (!s.naviConnected) PleosTag("내비 연결 중")
        }
        val (label, tone) = when (val r = s.routeStatus) {
            // 앱 재실행 시 이전 요청 결과는 알 수 없다 (내비의 현재 안내 조회는 NAVI_ROUTE 권한 필요, 미연결)
            RouteRequestStatus.None -> "이번 실행에서 보낸 기록 없음" to c.textSecondary
            RouteRequestStatus.Waiting -> "요청 보냄 · 내비 응답 대기" to c.textSecondary
            is RouteRequestStatus.Started -> "안내 시작됨 · ${r.name} · ${r.distanceMeters / 1000.0}km · ${r.durationSeconds / 60}분" to c.positive
            is RouteRequestStatus.Failed -> "안내 실패 · ${r.reason} · 목적지 정보는 그대로입니다" to c.negative
            RouteRequestStatus.Cancelled -> "내비에서 안내가 취소됨" to c.regulationOrange
        }
        BasicText(label, style = t.bodyLarge.copy(color = tone))
        s.lastRoutePlan?.let { BasicText("전달한 경로: ${it.stopLabels.joinToString(" → ")}", style = t.labelMedium.copy(color = c.textSecondary)) }
        val retry = s.routeStatus is RouteRequestStatus.Failed || s.routeStatus == RouteRequestStatus.Cancelled
        val sentForThisStop = s.lastRoutePlan?.stopLabels?.firstOrNull() == stop.label && !retry
        PleosButton(
            when {
                retry -> "다시 보내기"
                sentForThisStop -> "내비에 다시 보내기"
                else -> "내비 안내 보내기 (${stop.label}부터)"
            },
            style = if (sentForThisStop) PleosButtonStyle.Basic else PleosButtonStyle.Filled,
        ) { on(TripUiAction.AskRoute) }
    }
    PleosCard {
        BasicText("도착 확인", style = t.titleSmall.copy(color = c.textPrimary))
        BasicText("도착 신호를 차량에서 받을 수 없어 기사 확인으로 현장 작업을 시작합니다.", style = t.labelMedium.copy(color = c.textTertiary))
        PleosButton("${stop.label} 도착 · 현장 작업 시작") { on(TripUiAction.Arrived) }
    }
    PickupsCard(trip, on)
    IssuesCard(trip, on, showSampleButtons = true)
    StopManageCard(trip, on, allowQty = false)
    PleosButton("운행 종료", style = PleosButtonStyle.Basic) { on(TripUiAction.AskEndTrip) }
}


@Composable
internal fun RouteDialog(plan: RoutePlan, on: (TripUiAction) -> Unit) {
    val c = Pleos.colors
    val t = Pleos.type
    PleosSystemPopup(
        title = "내비에 안내를 보낼까요?",
        onDismiss = { on(TripUiAction.DismissRoute) },
        actions = listOf(
            PleosPopupAction("보내기") { on(TripUiAction.ConfirmRoute) },
            PleosPopupAction("취소", PleosButtonStyle.Basic) { on(TripUiAction.DismissRoute) },
        ),
    ) {
        BasicText(plan.stopLabels.joinToString(" → "), style = t.titleSmall.copy(color = c.textPrimary))
        BasicText((plan.waypoints + plan.destination).joinToString("\n") { it.name }, style = t.bodyMedium.copy(color = c.textSecondary))
        BasicText("진행 중인 안내가 있으면 새 경로로 바뀝니다.", style = t.labelMedium.copy(color = c.textTertiary))
    }
}

// ---------------------------------------------------------------- S04

