package com.loadmate.app.feature.trip.presentation.mismatch

import com.loadmate.app.feature.trip.presentation.shared.*
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
internal fun MismatchScreen(s: MismatchUiState, trip: Trip, on: (TripUiAction) -> Unit) {
    val c = Pleos.colors
    val t = Pleos.type
    val cmp = s.pendingConfirmation ?: return
    val stop = trip.currentStop
    PleosCard {
        BasicText("출발 전 확인", style = t.titleMedium.copy(color = c.textPrimary))
        BasicText("아직 완료로 저장하지 않았습니다.", style = t.bodyMedium.copy(color = c.textSecondary))
        cmp.differences.forEach { d ->
            val name = trip.kindNames[d.kind] ?: d.kind
            val hint = if (d.observed > d.expected) "${d.observed - d.expected}개가 더 남아 있는 것으로 보입니다" else "${d.expected - d.observed}개가 적게 보입니다"
            BasicText("$name: 예상 ${d.expected} / 관측 ${d.observed} · $hint", style = t.bodyLarge.copy(color = c.negative))
        }
    }
    ComparisonView(cmp, trip, "하차 반영 후 예상 잔량 대비")
    ZoneView(trip, cmp, highlightStop = stop)
    Column(verticalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
        PleosButton("남은 물품을 찾아 내림 · 다시 관측", enabled = !s.busy) { on(TripUiAction.RecheckAfterUnload) }
        val extra = cmp.differences.firstOrNull { it.observed > it.expected }
        if (extra != null) {
            PleosButton("더 보이는 것은 회수품이에요 · 회수 기록", style = PleosButtonStyle.Basic, enabled = !s.busy) {
                on(TripUiAction.OpenPickup(PickupDraft(itemName = "반품 박스", kind = extra.kind, qty = extra.observed - extra.expected)))
            }
        }
        PleosButton(
            if (cmp.state == ComparisonState.MISMATCH) "이미 다 내렸음 · 기사 확인으로 완료" else "직접 확인함 · 기사 확인으로 완료",
            style = PleosButtonStyle.Basic, enabled = !s.busy,
        ) { on(TripUiAction.DriverConfirmComplete) }
        BasicText("기사 확인으로 완료해도 관측 결과는 근거로 함께 저장됩니다.", style = t.labelMedium.copy(color = c.textTertiary))
        stop?.let { BasicText("${it.label} 하차 ${countsText(s.deliveredDraft, trip)} 기준", style = t.labelMedium.copy(color = c.textSecondary)) }
    }
}

// ---------------------------------------------------------------- 완료

