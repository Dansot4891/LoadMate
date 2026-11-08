package com.loadmate.app.feature.trip.presentation

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

// ---------------------------------------------------------------- 공통

@Composable
internal fun MessageBar(m: UiMessage, onClose: () -> Unit) {
    val c = Pleos.colors
    Row(
        Modifier.fillMaxWidth().background(c.surfaceHigh, RoundedCornerShape(PleosShape.small)).padding(PleosSpacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(m.text, Modifier.weight(1f), style = Pleos.type.bodyMedium.copy(color = if (m.isError) c.negative else c.textPrimary))
        BasicText("닫기", Modifier.clickable(onClick = onClose).padding(PleosSpacing.m), style = Pleos.type.bodyMedium.copy(color = c.textSecondary))
    }
}

@Composable
internal fun StopsPanel(trip: Trip, modifier: Modifier) {
    val c = Pleos.colors
    val t = Pleos.type
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
        PleosCard {
            BasicText("예상 잔량 (서버 계산)", style = t.labelMedium.copy(color = c.textTertiary))
            BasicText(
                trip.expectedRemaining?.let { countsText(it, trip) } ?: "기준 확정 전 · 계획 ${countsText(trip.plannedTotals, trip)}",
                style = t.titleSmall.copy(color = c.textPrimary),
            )
        }
        val current = trip.currentStop?.id
        trip.stops.forEach { st -> StopRow(st, trip, isCurrent = st.id == current && trip.baseline != null) }
    }
}

@Composable
private fun StopRow(st: Stop, trip: Trip, isCurrent: Boolean) {
    val c = Pleos.colors
    val t = Pleos.type
    Column(
        Modifier.fillMaxWidth()
            .background(if (isCurrent) c.surfaceAccent else c.surfaceBasic, RoundedCornerShape(PleosShape.medium))
            .padding(PleosSpacing.l),
        verticalArrangement = Arrangement.spacedBy(PleosSpacing.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m), verticalAlignment = Alignment.CenterVertically) {
            BasicText(st.label, style = t.titleSmall.copy(color = c.textPrimary))
            BasicText(st.name, Modifier.weight(1f), style = t.bodyMedium.copy(color = c.textPrimary))
            val (label, tone) = when (st.status) {
                StopStatus.PENDING -> (if (isCurrent) "현재" else "예정") to c.textSecondary
                StopStatus.COMPLETED -> "완료" to c.positive
                StopStatus.PARTIAL -> "부분 납품" to c.regulationOrange
                StopStatus.CANCELLED -> "취소" to c.textTertiary
                StopStatus.UNKNOWN -> "?" to c.textSecondary
            }
            BasicText(label, style = t.bodyMedium.copy(color = tone))
        }
        val cargo = st.cargo.joinToString(" · ") { "${it.itemName} ${it.deliveredQty}/${it.plannedQty}" }
        val how = when (st.resolution) {
            "driver_confirmed" -> " · 기사 확인"
            "matched" -> " · 관측 일치"
            else -> ""
        }
        BasicText("하차 $cargo$how", style = t.labelMedium.copy(color = c.textSecondary))
        st.cancelReason?.let { BasicText("취소 사유: $it", style = t.labelMedium.copy(color = c.textTertiary)) }
    }
}

/** 관측 입력: 카메라 API가 없어 샘플·기사 입력만 (원칙 5: 출처 표시) */
@Composable
internal fun ObservationInput(s: TripUiState, trip: Trip, on: (TripUiAction) -> Unit) {
    val c = Pleos.colors
    val t = Pleos.type
    PleosCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
            BasicText("적재 관측 입력", style = t.titleSmall.copy(color = c.textPrimary))
            PleosTag("카메라 미연결")
        }
        BasicText(
            "Pleos SDK에 카메라 API가 없어 시연에서는 샘플 값 또는 기사 입력으로 관측을 기록합니다.",
            style = t.labelMedium.copy(color = c.textTertiary),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
            listOf(ObservationSource.SAMPLE, ObservationSource.MANUAL).forEach { src ->
                PleosButton(
                    src.label,
                    style = if (s.observationSource == src) PleosButtonStyle.Filled else PleosButtonStyle.Basic,
                ) { on(TripUiAction.ObservationSourceSelect(src)) }
            }
        }
        trip.kindNames.forEach { (kind, name) ->
            PleosStepper(name, s.observationDraft[kind] ?: 0, { on(TripUiAction.ObservationCount(kind, it)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
            PleosButton("관측 기록", enabled = !s.busy) { on(TripUiAction.RecordObservation) }
            PleosButton("확인 불가 기록", style = PleosButtonStyle.Basic, enabled = !s.busy) { on(TripUiAction.RecordUnavailable) }
        }
    }
}

@Composable
internal fun ComparisonView(cmp: Comparison, trip: Trip, title: String) {
    val c = Pleos.colors
    val t = Pleos.type
    val (label, tone) = comparisonLabel(cmp.state)
    PleosCard {
        BasicText(title, style = t.labelMedium.copy(color = c.textTertiary))
        BasicText(label, style = t.titleSmall.copy(color = tone))
        BasicText("예상 ${countsText(cmp.expected, trip)}   /   관측 ${countsText(cmp.observed, trip)}", style = t.bodyLarge.copy(color = c.textPrimary))
        cmp.observation?.let {
            BasicText("관측 출처: ${it.source.label} · ${localTime(it.observedAt)}", style = t.labelMedium.copy(color = c.textSecondary))
        }
        if (cmp.countOnly) {
            BasicText("총량 검사입니다. 개별 물품이 맞는지는 확인하지 않습니다.", style = t.labelMedium.copy(color = c.textTertiary))
        }
    }
}

@Composable
private fun comparisonLabel(state: ComparisonState): Pair<String, Color> {
    val c = Pleos.colors
    return when (state) {
        ComparisonState.MATCH -> "관측과 일치" to c.positive
        ComparisonState.MISMATCH -> "관측과 다름 · 확인 필요" to c.negative
        ComparisonState.NO_OBSERVATION -> "관측 없음 · 수동 확인 필요" to c.regulationOrange
        ComparisonState.UNAVAILABLE -> "관측 확인 불가 · 수동 확인 필요" to c.regulationOrange
        ComparisonState.STALE -> "관측이 오래됨 · 다시 관측 필요" to c.regulationOrange
        ComparisonState.UNKNOWN -> "비교 상태 알 수 없음" to c.textSecondary
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
        BasicText("예상 잔량 ${countsText(trip.expectedRemaining, trip)}", style = t.bodyLarge.copy(color = c.textPrimary))
        BasicText("내비 연결을 해제했습니다.", style = t.labelMedium.copy(color = c.textTertiary))
    }
}

/** 서버 UTC ISO 시각 → 기기 현지 시각 HH:mm:ss */
internal fun localTime(iso: String): String = runCatching {
    java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
}.getOrDefault(iso)
