package com.loadmate.app.feature.trip.presentation.sitework

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
internal fun SiteWorkScreen(s: TripUiState, trip: Trip, on: (TripUiAction) -> Unit) {
    val c = Pleos.colors
    val t = Pleos.type
    val stop = trip.currentStop ?: return
    // 현장 화면이 보일 때만 뒷문 구독
    LifecycleStartEffect(stop.id) {
        on(TripUiAction.SiteVisible)
        onStopOrDispose { on(TripUiAction.SiteHidden) }
    }
    if (s.stopMemos.isNotEmpty()) PleosCard {
        BasicText("이 장소 메모 (${s.stopMemos.size})", style = t.titleSmall.copy(color = c.textPrimary))
        s.stopMemos.take(3).forEach { m ->
            BasicText("${if (m.shared) "공유" else "개인"} · ${m.body}", style = t.bodyMedium.copy(color = c.textPrimary))
        }
    }
    PleosCard {
        BasicText("현장 작업 · ${stop.label} ${stop.name}", style = t.titleMedium.copy(color = c.textPrimary))
        BasicText("하차 예정 ${countsText(stop.plannedCounts, trip)}", style = t.bodyLarge.copy(color = c.textPrimary))
        trip.kindNames.filterKeys { it in stop.plannedCounts }.forEach { (kind, name) ->
            PleosStepper("$name 하차", s.deliveredDraft[kind] ?: 0, { on(TripUiAction.DeliveredCount(kind, it)) }, max = stop.plannedCounts[kind] ?: 0)
        }
        BasicText("적재 위치: ${stop.cargo.joinToString(" · ") { "${it.itemName} ${zoneName(it.zone)}" }}", style = t.labelMedium.copy(color = c.textSecondary))
        PleosButton("회수·추가 상차 기록", style = PleosButtonStyle.Basic) { on(TripUiAction.OpenPickup()) }
    }
    PleosCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
            BasicText("뒷문", style = t.titleSmall.copy(color = c.textPrimary))
            PleosTag("에뮬레이터 신호")
        }
        val d = s.rearDoor
        val text = when (val conn = d.connection) {
            RearDoorConnection.Stopped -> "구독 안 함"
            is RearDoorConnection.Failed -> "신호 없음 · ${conn.reason}"
            RearDoorConnection.Subscribed -> when (d.isOpen) {
                true -> "열림 · 하역 중으로 보입니다"
                false -> if (d.updateCount > 1) "닫힘 · 하역이 끝났으면 완료 요청을 보내세요" else "닫힘"
                null -> "값 대기"
            }
        }
        BasicText(text, style = t.bodyLarge.copy(color = c.textPrimary))
        BasicText("뒷문 신호는 참고용이며 완료를 확정하지 않습니다.", style = t.labelMedium.copy(color = c.textTertiary))
    }
    ObservationInput(s, trip, on)
    // 하역 도중에는 예상(하차 반영 전)과 관측이 당연히 다르므로 판정을 보여 주지 않는다. 판정은 완료 요청 때 서버가 한다
    PleosCard {
        BasicText("최신 관측", style = t.labelMedium.copy(color = c.textTertiary))
        val obs = s.lastComparison?.observation
        BasicText(
            when {
                obs == null -> "아직 없음"
                !obs.available -> "확인 불가 · ${obs.source.label} · ${localTime(obs.observedAt)}"
                else -> "${countsText(obs.counts, trip)} · ${obs.source.label} · ${localTime(obs.observedAt)}"
            },
            style = t.bodyLarge.copy(color = c.textPrimary),
        )
        BasicText("완료 요청 시 서버가 하차 반영 후 예상 잔량과 비교합니다.", style = t.labelMedium.copy(color = c.textTertiary))
    }
    ZoneView(trip, s.lastComparison)
    PickupsCard(trip, on)
    IssuesCard(trip, on, showSampleButtons = false)
    Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
        PleosButton("하차 완료 요청 (${countsText(s.deliveredDraft, trip)})", enabled = !s.busy) { on(TripUiAction.RequestComplete) }
        PleosButton("운행 화면으로", style = PleosButtonStyle.Basic) { on(TripUiAction.BackToDriving) }
    }
}

// ---------------------------------------------------------------- S06

