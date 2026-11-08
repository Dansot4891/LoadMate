package com.loadmate.app.feature.trip.presentation.shared

import com.loadmate.app.core.designsystem.localTime
import com.loadmate.app.feature.trip.presentation.overview.OverviewScreen
import com.loadmate.app.feature.trip.presentation.loadcheck.LoadCheckScreen
import com.loadmate.app.feature.trip.presentation.driving.DrivingScreen
import com.loadmate.app.feature.trip.presentation.driving.RouteDialog
import com.loadmate.app.feature.trip.presentation.sitework.SiteWorkScreen
import com.loadmate.app.feature.trip.presentation.mismatch.MismatchScreen

import com.loadmate.app.core.pleos.RouteRequestStatus
import com.loadmate.app.core.pleos.routeRequestStatus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loadmate.app.core.designsystem.Pleos
import com.loadmate.app.core.designsystem.PleosButton
import com.loadmate.app.core.designsystem.PleosButtonStyle
import com.loadmate.app.core.designsystem.PleosCard
import com.loadmate.app.core.designsystem.PleosShape
import com.loadmate.app.core.designsystem.PleosSpacing
import com.loadmate.app.core.designsystem.PleosStepper
import com.loadmate.app.core.designsystem.PleosTag
import com.loadmate.app.core.pleos.RearDoorConnection
import com.loadmate.app.feature.trip.domain.Comparison
import com.loadmate.app.feature.trip.domain.ComparisonState
import com.loadmate.app.feature.trip.domain.ObservationSource
import com.loadmate.app.feature.trip.domain.Stop
import com.loadmate.app.feature.trip.domain.StopStatus
import com.loadmate.app.feature.trip.domain.Trip

@Composable
fun TripScreen(
    overview: com.loadmate.app.feature.trip.presentation.overview.OverviewViewModel,
    loadcheck: com.loadmate.app.feature.trip.presentation.loadcheck.LoadCheckViewModel,
    driving: com.loadmate.app.feature.trip.presentation.driving.DrivingViewModel,
    sitework: com.loadmate.app.feature.trip.presentation.sitework.SiteWorkViewModel,
    mismatch: com.loadmate.app.feature.trip.presentation.mismatch.MismatchViewModel,
) {
    val screenState by overview.state.collectAsStateWithLifecycle()
    val state = screenState
    val loadCheckState by loadcheck.state.collectAsStateWithLifecycle()
    val drivingState by driving.state.collectAsStateWithLifecycle()
    val siteWorkState by sitework.state.collectAsStateWithLifecycle()
    val mismatchState by mismatch.state.collectAsStateWithLifecycle()
    val action: (TripUiAction) -> Unit = { value ->
        when (state.step) {
            TripStep.OVERVIEW, TripStep.DONE -> overview.onAction(com.loadmate.app.feature.trip.presentation.overview.OverviewUiAction.from(value))
            TripStep.LOAD_CHECK -> loadcheck.onAction(com.loadmate.app.feature.trip.presentation.loadcheck.LoadCheckUiAction.from(value))
            TripStep.DRIVING -> driving.onAction(com.loadmate.app.feature.trip.presentation.driving.DrivingUiAction.from(value))
            TripStep.SITE_WORK -> sitework.onAction(com.loadmate.app.feature.trip.presentation.sitework.SiteWorkUiAction.from(value))
            TripStep.MISMATCH -> mismatch.onAction(com.loadmate.app.feature.trip.presentation.mismatch.MismatchUiAction.from(value))
        }
    }
    TripContent(state, loadCheckState, drivingState, siteWorkState, mismatchState, action)
}

@Composable
private fun TripContent(
    s: com.loadmate.app.feature.trip.presentation.overview.OverviewUiState,
    loadCheck: com.loadmate.app.feature.trip.presentation.loadcheck.LoadCheckUiState,
    driving: com.loadmate.app.feature.trip.presentation.driving.DrivingUiState,
    siteWork: com.loadmate.app.feature.trip.presentation.sitework.SiteWorkUiState,
    mismatch: com.loadmate.app.feature.trip.presentation.mismatch.MismatchUiState,
    on: (TripUiAction) -> Unit,
) {
    val c = Pleos.colors
    val t = Pleos.type
    Box(Modifier.fillMaxSize().background(c.backgroundPrimary)) {
        Column(Modifier.fillMaxSize().padding(PleosSpacing.xl), verticalArrangement = Arrangement.spacedBy(PleosSpacing.l)) {
            // 상단: 앱 이름 · 운행 · 출처
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.l)) {
                s.trip?.let {
                    BasicText(it.name, style = t.bodyLarge.copy(color = c.textSecondary))
                    if (it.dataSource == "sample") PleosTag("샘플 데이터")
                }
                Box(Modifier.weight(1f))
                PleosButton("새로고침", style = PleosButtonStyle.Basic, enabled = !s.loading) { on(TripUiAction.Refresh) }
            }
            s.message?.let { MessageBar(it) { on(TripUiAction.DismissMessage) } }
            s.offlineSince?.let {
                MessageBar(UiMessage("오프라인 · ${localTime(it.toString())} 에 받은 정보를 표시 중입니다. 변경 요청은 연결 후 다시 보내세요", true)) {
                    on(TripUiAction.Refresh)
                }
            }

            val trip = s.trip
            when {
                trip == null && s.loading -> BasicText("운행 정보를 불러오는 중…", style = t.bodyLarge.copy(color = c.textSecondary))
                trip == null -> PleosCard(Modifier.widthIn(max = 960.dp)) {
                    BasicText("운행 정보를 불러오지 못했습니다", style = t.titleSmall.copy(color = c.negative))
                    BasicText(s.loadError ?: "", style = t.bodyMedium.copy(color = c.textSecondary))
                    PleosButton("다시 시도") { on(TripUiAction.Refresh) }
                }
                else -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xl)) {
                    StopsPanel(trip, Modifier.weight(0.42f).fillMaxHeight())
                    Column(
                        Modifier.weight(0.58f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(PleosSpacing.l),
                    ) {
                        when (s.step) {
                            TripStep.OVERVIEW -> OverviewScreen(trip, on)
                            TripStep.LOAD_CHECK -> LoadCheckScreen(loadCheck, trip, on)
                            TripStep.DRIVING -> DrivingScreen(driving, trip, on)
                            TripStep.SITE_WORK -> SiteWorkScreen(siteWork, trip, on)
                            TripStep.MISMATCH -> MismatchScreen(mismatch, trip, on)
                            TripStep.DONE -> { DonePanel(trip); PickupsCard(trip, on) }
                        }
                    }
                }
            }
        }
        s.routeDialog?.let { RouteDialog(it, on) }
        s.pickupDraft?.let { PickupDialog(it, s.trip?.currentStop, s.busy, on) }
        if (s.endDialog) s.trip?.let { EndTripDialog(it, on) }
    }
}

