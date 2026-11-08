package com.loadmate.app.feature.trip.presentation.loadcheck

import com.loadmate.app.feature.trip.presentation.*

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
internal fun LoadCheckScreen(s: TripUiState, trip: Trip, on: (TripUiAction) -> Unit) {
    val c = Pleos.colors
    val t = Pleos.type
    PleosCard {
        BasicText("최초 적재 확인", style = t.titleMedium.copy(color = c.textPrimary))
        BasicText("검수 목록: ${countsText(trip.plannedTotals, trip)}", style = t.bodyLarge.copy(color = c.textPrimary))
        BasicText("관측을 기록하고 수량이 맞으면 기준으로 확정합니다. 다르면 아래 수량을 고쳐 확정할 수 있습니다.", style = t.bodyMedium.copy(color = c.textSecondary))
    }
    ObservationInput(s, trip, on)
    (s.lastComparison ?: trip.comparison).let {
        ComparisonView(it, trip, "검수 목록 대비")
        ZoneView(trip, it)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
        PleosButton("이 수량으로 기준 확정 (${countsText(s.observationDraft, trip)})", enabled = !s.busy) { on(TripUiAction.ConfirmBaseline) }
        PleosButton("뒤로", style = PleosButtonStyle.Basic) { on(TripUiAction.BackToOverview) }
    }
}

// ---------------------------------------------------------------- S02·S03

