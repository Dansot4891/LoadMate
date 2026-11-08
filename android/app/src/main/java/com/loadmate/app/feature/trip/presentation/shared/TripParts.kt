package com.loadmate.app.feature.trip.presentation.shared

import com.loadmate.app.R
import com.loadmate.app.shared.designsystem.components.PleosIconButton
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
internal fun ZoneView(trip: Trip, comparison: Comparison?, highlightStop: Stop? = null) {
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
                val hot = highlightStop?.cargo?.any { it.zone == zone } == true && comparison?.differences?.isNotEmpty() == true
                Column(
                    Modifier.weight(1f)
                        .background(if (hot) c.surfaceAccent else c.surfaceLow, RoundedCornerShape(PleosShape.small))
                        .border(PleosShape.hairline, if (hot) c.negative else c.divider, RoundedCornerShape(PleosShape.small))
                        .padding(PleosSpacing.m),
                    verticalArrangement = Arrangement.spacedBy(PleosSpacing.xs),
                ) {
                    BasicText(name, style = t.labelMedium.copy(color = c.textTertiary))
                    BasicText("방문지 ${labelsByZone[zone]?.joinToString(",") ?: "-"}", style = t.bodyMedium.copy(color = c.textPrimary))
                    BasicText("계획 ${countsText(planned[zone], trip).ifBlank { "-" }}", style = t.labelMedium.copy(color = c.textSecondary))
                    observedZones?.let { BasicText("관측 ${countsText(it[zone], trip)}", style = t.labelMedium.copy(color = if (hot) c.negative else c.textSecondary)) }
                }
            }
        }
        BasicText("← 운전석 방향        뒷문 →   구역은 계획·관측 기준이며 개별 물품 위치를 보장하지 않습니다.", style = t.labelSmall.copy(color = c.textTertiary))
    }
}

/** 방문지 관리 (F04·F18·F29). 남은 방문지만 순서 변경·취소. 계획 수량은 기준 확정 전 S01 에서만 */
@Composable
internal fun StopManageCard(trip: Trip, on: (TripUiAction) -> Unit, allowQty: Boolean) {
    val c = Pleos.colors
    val t = Pleos.type
    var cancelTarget by remember { mutableStateOf<Stop?>(null) }
    PleosCard {
        BasicText("방문지 관리", style = t.titleSmall.copy(color = c.textPrimary))
        val pending = trip.pendingStops
        pending.forEachIndexed { i, st ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.s)) {
                BasicText("${st.label} ${st.name}", Modifier.weight(1f), style = t.bodyMedium.copy(color = c.textPrimary))
                PleosIconButton(R.drawable.pleos_ic_arrow_up, "${st.label} 위로", enabled = i > 0) { on(TripUiAction.MoveStop(st.id, up = true)) }
                PleosIconButton(R.drawable.pleos_ic_arrow_down, "${st.label} 아래로", enabled = i < pending.lastIndex) { on(TripUiAction.MoveStop(st.id, up = false)) }
                PleosButton("취소", style = PleosButtonStyle.Basic, size = PleosButtonSize.Small) { cancelTarget = st }
            }
            if (allowQty) st.cargo.forEach { l ->
                PleosStepper("  ${l.itemName}", l.plannedQty, { on(TripUiAction.ChangeQty(st.id, l.id, it)) }, max = 99)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
            SAMPLE_PLACES.forEachIndexed { i, p ->
                if (trip.stops.none { it.name == p.name }) {
                    PleosButton(p.name.substringBefore(" ("), style = PleosButtonStyle.Basic, size = PleosButtonSize.Small, leadingIcon = R.drawable.pleos_ic_plus) { on(TripUiAction.AddSampleStop(i)) }
                }
            }
        }
        BasicText(
            if (trip.baseline == null) "추가 방문지는 샘플 장소이며 박스 1개가 계획에 더해집니다."
            else "기준 확정 후 추가하면 추가 상차로 예상 잔량에 더해집니다. 순서를 바꾸면 내비에 다시 보내야 합니다.",
            style = t.labelMedium.copy(color = c.textTertiary),
        )
    }
    cancelTarget?.let { st ->
        PleosSystemPopup(
            title = "${st.label} 방문을 취소할까요?",
            onDismiss = { cancelTarget = null },
            buttons = PleosPopupButtons.Vertical,
            actions = listOf("수령인 부재", "주소 오류", "고객 요청").map { reason ->
                PleosPopupAction(reason, PleosButtonStyle.Basic) { on(TripUiAction.CancelStop(st.id, reason)); cancelTarget = null }
            },
        ) {
            BasicText("배송품 ${countsText(st.plannedCounts, trip)}은 차에 남습니다.", style = t.bodyLarge.copy(color = c.textSecondary))
        }
    }
}

/** 회수 입력 (F44). 음성 초안도 이 형식으로 확인한다 */
@Composable
internal fun PickupDialog(d: PickupDraft, stop: Stop?, busy: Boolean, on: (TripUiAction) -> Unit) {
    val c = Pleos.colors
    val t = Pleos.type
    val items = listOf("빈 용기" to "container", "반품 박스" to "box", "팔레트" to "pallet")
    PleosSystemPopup(
        title = "회수·추가 상차 기록",
        onDismiss = { on(TripUiAction.DismissPickup) },
        size = PleosSystemPopupSize.Large,
        actions = listOf(
            PleosPopupAction("기록 (${d.itemName} ${d.qty}개 → ${d.returnPlace})", enabled = !busy) { on(TripUiAction.SubmitPickup) },
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
        BasicText("회수품은 배송품과 따로 관리되고 예상 잔량에 더해집니다.", style = t.labelMedium.copy(color = c.textTertiary))
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

/** 적재 이상 의심 (F30·F31·F33·F34). 카메라가 없어 샘플 관측 버튼으로 재현한다 */
@Composable
internal fun IssuesCard(trip: Trip, on: (TripUiAction) -> Unit, showSampleButtons: Boolean) {
    val c = Pleos.colors
    val t = Pleos.type
    if (trip.openIssues.isEmpty() && !showSampleButtons) return
    PleosCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
            BasicText(if (trip.openIssues.isEmpty()) "적재 이상 없음" else "적재 이상", style = t.titleSmall.copy(color = c.textPrimary))
            PleosTag("카메라 미연결 · 샘플 관측")
        }
        trip.openIssues.forEach { i ->
            BasicText("${issueLabel(i.kind)} · ${zoneName(i.zone)} · ${localTime(i.observedAt)} · ${i.source.label}", style = t.bodyLarge.copy(color = c.negative))
            Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.s)) {
                PleosButton("이상 없음", style = PleosButtonStyle.Basic) { on(TripUiAction.ResolveIssue(i.id, "no_issue")) }
                PleosButton("재배치함", style = PleosButtonStyle.Basic) { on(TripUiAction.ResolveIssue(i.id, "rearranged")) }
                PleosButton("처리 필요", style = PleosButtonStyle.Basic) { on(TripUiAction.ResolveIssue(i.id, "needs_action")) }
            }
        }
        BasicText("의심 기록입니다. 물품 내부 파손이나 책임을 확정하지 않습니다.", style = t.labelMedium.copy(color = c.textTertiary))
        if (showSampleButtons) {
            // 실수로 기록되지 않도록 시연 도구는 펼쳐야 보인다
            var open by remember { mutableStateOf(false) }
            if (!open) PleosButton("시연 도구 열기 (샘플 이상 관측)", style = PleosButtonStyle.Basic) { open = true }
            else Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.s), verticalAlignment = Alignment.CenterVertically) {
                BasicText("샘플 관측:", style = t.labelMedium.copy(color = c.textTertiary))
                listOf("shift" to "이동", "fall" to "넘어짐", "leak" to "누액").forEach { (k, name) ->
                    PleosButton("$name 의심", style = PleosButtonStyle.Basic) { on(TripUiAction.RecordSampleIssue(k)); open = false }
                }
                PleosButton("닫기", style = PleosButtonStyle.Basic) { open = false }
            }
        }
    }
}

@Composable
internal fun EndTripDialog(trip: Trip, on: (TripUiAction) -> Unit) {
    val c = Pleos.colors
    val t = Pleos.type
    val unfinished = trip.pendingStops.isNotEmpty() || trip.loadedPickups.isNotEmpty() || trip.openIssues.isNotEmpty()
    PleosSystemPopup(
        title = "운행을 종료할까요?",
        onDismiss = { on(TripUiAction.DismissEndTrip) },
        buttons = if (unfinished) PleosPopupButtons.Vertical else PleosPopupButtons.Horizontal,
        actions = if (unfinished) {
            listOf("기사 교대로 인계", "운행 시간 종료", "차량 문제").map { r -> PleosPopupAction(r, PleosButtonStyle.Basic) { on(TripUiAction.EndTrip(r)) } }
        } else {
            listOf(
                PleosPopupAction("종료") { on(TripUiAction.EndTrip(null)) },
                PleosPopupAction("닫기", PleosButtonStyle.Basic) { on(TripUiAction.DismissEndTrip) },
            )
        },
    ) {
        if (unfinished) {
            BasicText(
                listOfNotNull(
                    trip.pendingStops.takeIf { it.isNotEmpty() }?.let { "남은 방문지 ${it.joinToString(",") { s -> s.label }}" },
                    trip.loadedPickups.takeIf { it.isNotEmpty() }?.let { "미반납 회수품 ${it.size}건" },
                    trip.openIssues.takeIf { it.isNotEmpty() }?.let { "미해결 이상 ${it.size}건" },
                ).joinToString(" · "),
                style = t.bodyLarge.copy(color = c.negative),
            )
            BasicText("남은 업무가 있어 종료 사유를 기록합니다. 다른 기사가 이어받으면 '장비·인계'에서 인계하세요.", style = t.bodyMedium.copy(color = c.textSecondary))
        } else {
            BasicText("모든 업무가 끝났습니다.", style = t.bodyLarge.copy(color = c.textSecondary))
        }
    }
}

internal fun zoneName(zone: String?) = ZONE_ORDER.firstOrNull { it.first == zone }?.second ?: "구역 미상"
