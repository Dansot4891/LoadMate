package com.loadmate.app.feature.trip.presentation.shared

import com.loadmate.app.shared.designsystem.components.PleosListItem
import com.loadmate.app.shared.designsystem.components.PleosSegmentedControl
import com.loadmate.app.shared.format.localTime
import com.loadmate.app.feature.trip.presentation.overview.OverviewScreen
import com.loadmate.app.feature.trip.presentation.driving.DrivingScreen
import com.loadmate.app.feature.trip.presentation.driving.RouteDialog

import com.loadmate.app.core.pleos.RouteRequestStatus
import com.loadmate.app.core.pleos.routeRequestStatus

import androidx.compose.foundation.background
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
import com.loadmate.app.shared.designsystem.theme.Pleos
import com.loadmate.app.shared.designsystem.components.PleosButton
import com.loadmate.app.shared.designsystem.components.PleosButtonStyle
import com.loadmate.app.shared.designsystem.project.PleosCard
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.components.PleosStepper
import com.loadmate.app.shared.designsystem.components.PleosTextButton
import com.loadmate.app.core.pleos.RearDoorConnection
import com.loadmate.app.feature.trip.domain.Comparison
import com.loadmate.app.feature.trip.domain.ComparisonState
import com.loadmate.app.feature.trip.domain.ObservationSource
import com.loadmate.app.feature.trip.domain.Stop
import com.loadmate.app.feature.trip.domain.StopStatus
import com.loadmate.app.feature.trip.domain.Trip

// ---------------------------------------------------------------- 공통

@Composable
internal fun MessageBar(m: UiMessage, onClose: () -> Unit) {
    val c = Pleos.colors
    Row(
        Modifier.fillMaxWidth().background(c.surfaceHigh, RoundedCornerShape(PleosShape.small)).padding(PleosSpacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(m.text, Modifier.weight(1f), style = Pleos.type.bodyMedium.copy(color = if (m.isError) c.negative else c.textPrimary))
        PleosTextButton("닫기", onClick = onClose)
    }
}

// ---------------------------------------------------------------- S01

@Composable
internal fun DonePanel(trip: Trip) {
    val c = Pleos.colors
    val t = Pleos.type
    PleosCard {
        if (trip.status == com.loadmate.app.feature.trip.domain.TripStatus.ENDED_INCOMPLETE) {
            BasicText("운행 종료 (미완료)", style = t.titleMedium.copy(color = c.regulationOrange))
            BasicText("사유: ${trip.endReason ?: "-"} · 남은 방문지 ${trip.pendingStops.joinToString(",") { it.label }.ifBlank { "없음" }}", style = t.bodyLarge.copy(color = c.textPrimary))
        } else {
            BasicText("운행 완료", style = t.titleMedium.copy(color = c.positive))
        }
        BasicText("남은 짐 ${countsText(trip.expectedRemaining, trip)}", style = t.bodyLarge.copy(color = c.textPrimary))
    }
}
