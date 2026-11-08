package com.loadmate.app.feature.fleet.presentation.equipment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loadmate.app.core.designsystem.Pleos
import com.loadmate.app.core.designsystem.PleosButton
import com.loadmate.app.core.designsystem.PleosButtonStyle
import com.loadmate.app.core.designsystem.PleosCard
import com.loadmate.app.core.designsystem.PleosSpacing
import com.loadmate.app.core.designsystem.PleosTag
import com.loadmate.app.core.designsystem.Field

private val STATUS = listOf("ok" to "정상", "missing" to "찾지 못함", "broken" to "고장", "lent" to "대여 중", "not_returned" to "미반납")

@Composable
fun FleetScreen(vm: FleetViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.onAction(FleetUiAction.Refresh) }
    val on = vm::onAction
    val c = Pleos.colors
    val t = Pleos.type
    val name = { id: String? -> s.drivers.firstOrNull { it.id == id }?.name ?: id ?: "-" }
    Row(Modifier.fillMaxSize().padding(PleosSpacing.xl), horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xl)) {
        Column(Modifier.weight(0.5f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PleosSpacing.l)) {
            PleosCard {
                BasicText("지금 조작하는 기사", style = t.titleSmall.copy(color = c.textPrimary))
                BasicText("시연용 사용자 선택입니다. 로그인이나 실제 기사 인증은 아닙니다. 기록·장비 상태 변경의 작성자로 남습니다.", style = t.labelMedium.copy(color = c.textTertiary))
                Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.s)) {
                    s.drivers.forEach { d ->
                        PleosButton(d.name, style = if (d.id == s.driverId) PleosButtonStyle.Filled else PleosButtonStyle.Basic) { on(FleetUiAction.SelectDriver(d.id)) }
                    }
                }
                BasicText(
                    if (s.tripDriverId == null) "서버에 기록된 운행 담당 기사: 없음 · 첫 인계는 지금 조작하는 기사 이름으로 요청"
                    else "서버에 기록된 운행 담당 기사: ${name(s.tripDriverId)}",
                    style = t.bodyMedium.copy(color = c.textSecondary),
                )
            }
            PleosCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
                    BasicText("차량에 비치된 공용 도구", style = t.titleSmall.copy(color = c.textPrimary))
                    PleosTag("샘플 차량")
                }
                BasicText("핸드카트·고정 끈·공구함처럼 기사들이 함께 쓰는 차량 장비입니다. 배송할 물품이나 적재 수량에는 포함되지 않습니다.", style = t.labelMedium.copy(color = c.textSecondary))
                s.equipment.forEach { e ->
                    BasicText("${e.name} · ${e.location}", style = t.bodyLarge.copy(color = if (e.status == "ok") c.textPrimary else c.negative))
                    Column(verticalArrangement = Arrangement.spacedBy(PleosSpacing.xs)) {
                        listOf(STATUS.take(3), STATUS.drop(3)).forEach { group ->
                            Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xs)) {
                                group.forEach { (k, label) ->
                                    PleosButton(label, enabled = s.driverId != null && !s.busy, style = if (e.status == k) PleosButtonStyle.Filled else PleosButtonStyle.Basic) { on(FleetUiAction.SetEquipment(e.id, k)) }
                                }
                            }
                        }
                    }
                    e.updatedBy?.let { BasicText("마지막 수정: ${name(it)}", style = t.labelSmall.copy(color = c.textTertiary)) }
                }
                if (s.driverId == null) BasicText("상태를 기록하려면 위에서 조작 기사를 선택하세요.", style = t.labelMedium.copy(color = c.textTertiary))
            }
        }
        Column(Modifier.weight(0.5f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PleosSpacing.l)) {
            PleosCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
                    BasicText("교대 시 전달할 내용 미리보기", style = t.titleSmall.copy(color = c.textPrimary))
                    PleosTag("규칙 기반 자동 요약")
                }
                BasicText("남은 배송·회수품·문제·장비 이상·공유 메모를 모읍니다. 개인 메모는 제외됩니다.", style = t.labelMedium.copy(color = c.textSecondary))
                s.preview?.lines?.forEach { BasicText("· $it", style = t.bodyMedium.copy(color = c.textPrimary)) }
            }
            val h = s.handover
            if (h != null && !h.accepted) {
                PleosCard {
                    BasicText("인수 대기: ${name(h.fromDriverId)} → ${name(h.toDriverId)}", style = t.titleSmall.copy(color = c.regulationOrange))
                    if (h.note.isNotBlank()) BasicText("전달 사항: ${h.note}", style = t.bodyMedium.copy(color = c.textPrimary))
                    h.lines.forEach { BasicText("· $it", style = t.labelMedium.copy(color = c.textSecondary)) }
                    if (s.driverId == h.toDriverId) PleosButton("내용 확인 · 인수 확정") { on(FleetUiAction.AcceptHandover) }
                    else BasicText("위에서 '지금 조작하는 기사'를 ${name(h.toDriverId)}(으)로 바꾼 뒤 인수하세요. 인수 전에는 운행 담당자가 바뀌지 않습니다.", style = t.labelMedium.copy(color = c.textTertiary))
                }
            } else {
                PleosCard {
                    BasicText("기사 교대 · 인계 요청", style = t.titleSmall.copy(color = c.textPrimary))
                    BasicText("운행 담당 기사가 받는 기사를 선택해 요청합니다. 받는 기사가 내용을 확인하고 인수하면 운행 담당자가 바뀝니다.", style = t.labelMedium.copy(color = c.textSecondary))
                    if (h?.accepted == true) BasicText("최근 인계: ${name(h.fromDriverId)} → ${name(h.toDriverId)} 인수 완료", style = t.labelMedium.copy(color = c.positive))
                    if (s.tripDriverId == null) BasicText("이 샘플 운행은 담당 기사가 기록되지 않았습니다. 지금 조작하는 기사가 첫 인계 요청을 보낼 수 있습니다.", style = t.labelMedium.copy(color = c.textTertiary))
                    else if (s.driverId != s.tripDriverId) BasicText("인계 요청은 현재 운행 담당 기사(${name(s.tripDriverId)})로 선택한 뒤 할 수 있습니다.", style = t.labelMedium.copy(color = c.textTertiary))
                    Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.s)) {
                        BasicText("받는 기사", style = t.bodyLarge.copy(color = c.textPrimary))
                        s.drivers.filter { it.id != (s.tripDriverId ?: s.driverId) }.forEach { d ->
                            PleosButton(d.name, style = if (d.id == s.toDriverId) PleosButtonStyle.Filled else PleosButtonStyle.Basic) { on(FleetUiAction.ToDriver(d.id)) }
                        }
                    }
                    Field(s.note, "전달 사항 (예: C 주차 공간 좁음)", Modifier.fillMaxWidth()) { on(FleetUiAction.Note(it)) }
                    PleosButton("인계 요청 보내기", enabled = !s.busy && s.toDriverId != null && s.driverId != null && (s.tripDriverId == null || s.driverId == s.tripDriverId)) { on(FleetUiAction.CreateHandover) }
                    BasicText("공유 기록과 남은 업무만 넘어갑니다. 개인 메모는 포함되지 않습니다.", style = t.labelMedium.copy(color = c.textTertiary))
                }
            }
            s.message?.let { BasicText(it, style = t.bodyMedium.copy(color = c.textSecondary)) }
        }
    }
}
