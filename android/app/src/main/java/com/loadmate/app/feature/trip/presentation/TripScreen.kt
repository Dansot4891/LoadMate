package com.loadmate.app.feature.trip.presentation

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
fun TripScreen(vm: TripViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    TripContent(state, vm::onAction)
}

@Composable
private fun TripContent(s: TripUiState, on: (TripUiAction) -> Unit) {
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
                            TripStep.OVERVIEW -> OverviewPanel(trip, on)
                            TripStep.LOAD_CHECK -> LoadCheckPanel(s, trip, on)
                            TripStep.DRIVING -> DrivingPanel(s, trip, on)
                            TripStep.SITE_WORK -> SiteWorkPanel(s, trip, on)
                            TripStep.MISMATCH -> MismatchPanel(s, trip, on)
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

// ---------------------------------------------------------------- 공통

@Composable
private fun MessageBar(m: UiMessage, onClose: () -> Unit) {
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
private fun StopsPanel(trip: Trip, modifier: Modifier) {
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
private fun ObservationInput(s: TripUiState, trip: Trip, on: (TripUiAction) -> Unit) {
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
private fun ComparisonView(cmp: Comparison, trip: Trip, title: String) {
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
private fun OverviewPanel(trip: Trip, on: (TripUiAction) -> Unit) {
    val c = Pleos.colors
    val t = Pleos.type
    PleosCard {
        BasicText("운행 준비", style = t.titleMedium.copy(color = c.textPrimary))
        BasicText("방문지 ${trip.stops.size}곳 · 배송품 ${countsText(trip.plannedTotals, trip)}", style = t.bodyLarge.copy(color = c.textPrimary))
        BasicText("출발 전에 적재 공간을 확인하고 최초 기준을 확정합니다.", style = t.bodyMedium.copy(color = c.textSecondary))
        PleosButton("적재 확인 시작") { on(TripUiAction.OpenLoadCheck) }
    }
    StopManageCard(trip, on, allowQty = true)
}

// ---------------------------------------------------------------- S05

@Composable
private fun LoadCheckPanel(s: TripUiState, trip: Trip, on: (TripUiAction) -> Unit) {
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

@Composable
private fun DrivingPanel(s: TripUiState, trip: Trip, on: (TripUiAction) -> Unit) {
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
private fun RouteDialog(plan: RoutePlan, on: (TripUiAction) -> Unit) {
    val c = Pleos.colors
    val t = Pleos.type
    Box(Modifier.fillMaxSize().background(c.overlayDefault).clickable { on(TripUiAction.DismissRoute) }, contentAlignment = Alignment.Center) {
        PleosCard(Modifier.widthIn(max = 880.dp).clickable(enabled = false) {}) {
            BasicText("내비에 안내를 보낼까요?", style = t.titleMedium.copy(color = c.textPrimary))
            BasicText(plan.stopLabels.joinToString(" → "), style = t.titleSmall.copy(color = c.textPrimary))
            BasicText((plan.waypoints + plan.destination).joinToString("\n") { it.name }, style = t.bodyMedium.copy(color = c.textSecondary))
            BasicText("진행 중인 안내가 있으면 새 경로로 바뀝니다.", style = t.labelMedium.copy(color = c.textTertiary))
            Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
                PleosButton("보내기") { on(TripUiAction.ConfirmRoute) }
                PleosButton("취소", style = PleosButtonStyle.Basic) { on(TripUiAction.DismissRoute) }
            }
        }
    }
}

// ---------------------------------------------------------------- S04

@Composable
private fun SiteWorkPanel(s: TripUiState, trip: Trip, on: (TripUiAction) -> Unit) {
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

@Composable
private fun MismatchPanel(s: TripUiState, trip: Trip, on: (TripUiAction) -> Unit) {
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

@Composable
private fun DonePanel(trip: Trip) {
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
