package com.loadmate.app.feature.trip.presentation.shared

import com.loadmate.app.shared.designsystem.components.PleosSegmentedControl
import com.loadmate.app.shared.designsystem.components.PleosSystemPopup
import com.loadmate.app.shared.designsystem.components.PleosSystemPopupSize
import com.loadmate.app.shared.designsystem.components.PleosPopupAction
import com.loadmate.app.shared.designsystem.components.PleosPopupButtons
import com.loadmate.app.shared.format.localTime
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.loadmate.app.shared.designsystem.theme.Pleos
import com.loadmate.app.shared.designsystem.components.PleosButton
import com.loadmate.app.shared.designsystem.components.PleosButtonSize
import com.loadmate.app.shared.designsystem.components.PleosButtonStyle
import com.loadmate.app.shared.designsystem.project.PleosCard
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.components.PleosStepper
import com.loadmate.app.shared.designsystem.project.PleosTag
import com.loadmate.app.feature.trip.domain.Comparison
import com.loadmate.app.feature.trip.domain.Stop
import com.loadmate.app.feature.trip.domain.StopStatus
import com.loadmate.app.feature.trip.domain.Trip

private val ZONE_ORDER = listOf("front" to "앞쪽", "middle" to "가운데", "rear" to "뒷문 쪽")

/** 적재 공간 2D 배치 (F12·F13). 계획 위치와 최신 관측(구역별) 비교. 구역 연결이 불확실하면 표시하지 않는다 */
@Composable
internal fun ZoneView(trip: Trip, comparison: Comparison?) {
    val c = Pleos.colors
    val t = Pleos.type
    val planned = plannedZones(trip)
    val labelsByZone = trip.stops.filter { it.status == StopStatus.PENDING }
        .flatMap { st -> st.cargo.mapNotNull { l -> l.zone?.let { it to st.label } } }
        .groupBy({ it.first }, { it.second }).mapValues { it.value.distinct() }
    val obs = comparison?.observation
    val observedZones = obs?.zones
    PleosCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
            BasicText("적재 공간 (위에서 본 배치)", style = t.titleSmall.copy(color = c.textPrimary))
            if (obs != null && observedZones != null) PleosTag(obs.source.label)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PleosSpacing.s)) {
            ZONE_ORDER.forEach { (zone, name) ->
                Column(
                    Modifier.weight(1f)
                        .background(c.surfaceLow, RoundedCornerShape(PleosShape.small))
                        .border(PleosShape.hairline, c.divider, RoundedCornerShape(PleosShape.small))
                        .padding(PleosSpacing.m),
                    verticalArrangement = Arrangement.spacedBy(PleosSpacing.xs),
                ) {
                    BasicText(name, style = t.labelMedium.copy(color = c.textTertiary))
                    BasicText("방문지 ${labelsByZone[zone]?.joinToString(",") ?: "-"}", style = t.bodyMedium.copy(color = c.textPrimary))
                    BasicText("계획 ${countsText(planned[zone], trip).ifBlank { "-" }}", style = t.labelMedium.copy(color = c.textSecondary))
                    observedZones?.let { BasicText("확인 ${countsText(it[zone], trip)}", style = t.labelMedium.copy(color = c.textSecondary)) }
                }
            }
        }
        BasicText("← 운전석        뒷문 →", style = t.labelSmall.copy(color = c.textTertiary))
    }
}

/** 회수 입력 (F44). 음성 초안도 이 형식으로 확인한다 */
@Composable
internal fun PickupDialog(d: PickupDraft, stop: Stop?, busy: Boolean, on: (TripUiAction) -> Unit) {
    val c = Pleos.colors
    val t = Pleos.type
    val items = listOf("빈 용기" to "container", "반품 박스" to "box", "팔레트" to "pallet")
    PleosSystemPopup(
        title = "회수 기록",
        onDismiss = { on(TripUiAction.DismissPickup) },
        size = PleosSystemPopupSize.Large,
        actions = listOf(
            PleosPopupAction("기록", enabled = !busy) { on(TripUiAction.SubmitPickup) },
            PleosPopupAction("취소", PleosButtonStyle.Basic) { on(TripUiAction.DismissPickup) },
        ),
    ) {
        BasicText("장소: ${stop?.let { "${it.label} ${it.name}" } ?: "현재 위치 미정"}", style = t.bodyMedium.copy(color = c.textSecondary))
        PleosSegmentedControl(items, items.firstOrNull { it.first == d.itemName }, { it.first }) { (name, kind) ->
            on(TripUiAction.EditPickup(d.copy(itemName = name, kind = kind)))
        }
        PleosStepper("수량", d.qty, { on(TripUiAction.EditPickup(d.copy(qty = it))) }, min = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.l)) {
            BasicText("반납지", style = t.bodyLarge.copy(color = c.textPrimary))
            PleosSegmentedControl(listOf("창고", "본사", "거래처"), d.returnPlace, { it }) { place ->
                on(TripUiAction.EditPickup(d.copy(returnPlace = place)))
            }
        }
        BasicText("남은 짐에 더해집니다.", style = t.labelMedium.copy(color = c.textTertiary))
    }
}

/** 경로 짐과 실린 짐이 다를 때 고치도록 알린다 */
@Composable
internal fun RouteLoadGapCard(trip: Trip) {
    val gap = routeLoadGapText(trip) ?: return
    PleosCard {
        BasicText("경로 짐과 실린 짐이 달라요", style = Pleos.type.titleSmall.copy(color = Pleos.colors.negative))
        BasicText("$gap · 아래에서 수량을 맞춰 주세요", style = Pleos.type.bodyMedium.copy(color = Pleos.colors.textPrimary))
    }
}

/** 회수품 현황 (F46·F48) */
@Composable
internal fun PickupsCard(trip: Trip, on: (TripUiAction) -> Unit) {
    if (trip.pickups.isEmpty()) return
    val c = Pleos.colors
    val t = Pleos.type
    PleosCard {
        BasicText("회수품", style = t.titleSmall.copy(color = c.textPrimary))
        trip.pickups.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
                val from = trip.stops.firstOrNull { it.id == p.stopId }?.label ?: "-"
                BasicText("${p.itemName} ${p.qty}개 · $from 회수 → ${p.returnPlace}", Modifier.weight(1f), style = t.bodyMedium.copy(color = c.textPrimary))
                if (p.loaded) PleosButton("반납 완료", style = PleosButtonStyle.Basic, size = PleosButtonSize.Small) { on(TripUiAction.ReturnPickup(p.id)) }
                else BasicText("반납됨", style = t.bodyMedium.copy(color = c.positive))
            }
        }
    }
}

/** 적재 이상 의심 (F30·F31·F33·F34). 이상이 있을 때만 빨간 문구로 보이고 기사는 이상 없음으로 닫는다 */
@Composable
internal fun IssuesCard(trip: Trip, on: (TripUiAction) -> Unit) {
    if (trip.openIssues.isEmpty()) return
    val c = Pleos.colors
    val t = Pleos.type
    PleosCard {
        trip.openIssues.forEach { i ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
                BasicText("${issueLabel(i.kind)} · ${zoneName(i.zone)} · ${localTime(i.observedAt)}", Modifier.weight(1f), style = t.bodyLarge.copy(color = c.negative))
                PleosButton("이상 없음", style = PleosButtonStyle.Basic) { on(TripUiAction.ResolveIssue(i.id, "no_issue")) }
            }
        }
    }
}

/** 남은 업무가 있으면 서버 기록에 사유가 필요하다. 기사에게 고르게 하지 않는다 */
private const val END_REASON_BY_DRIVER = "기사가 운행 종료"

@Composable
internal fun EndTripDialog(trip: Trip, on: (TripUiAction) -> Unit) {
    val unfinished = trip.pendingStops.isNotEmpty() || trip.loadedPickups.isNotEmpty() || trip.openIssues.isNotEmpty()
    PleosSystemPopup(
        title = "운행을 종료할까요?",
        onDismiss = { on(TripUiAction.DismissEndTrip) },
        actions = listOf(
            PleosPopupAction("확인") { on(TripUiAction.EndTrip(END_REASON_BY_DRIVER.takeIf { unfinished })) },
            PleosPopupAction("취소", PleosButtonStyle.Basic) { on(TripUiAction.DismissEndTrip) },
        ),
    )
}

internal fun zoneName(zone: String?) = ZONE_ORDER.firstOrNull { it.first == zone }?.second ?: "구역 미상"
