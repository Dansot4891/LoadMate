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
import com.loadmate.app.feature.records.presentation.records.Field

private val STATUS = listOf("ok" to "정상", "missing" to "없음", "broken" to "고장", "lent" to "대여", "not_returned" to "미반납")

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
                BasicText("기사 선택", style = t.titleSmall.copy(color = c.textPrimary))
                BasicText("로그인 대신 사용자 선택입니다. 얼굴 인식 등으로 자동 판별하지 않습니다.", style = t.labelMedium.copy(color = c.textTertiary))
                Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.s)) {
                    s.drivers.forEach { d ->
                        PleosButton(d.name, style = if (d.id == s.driverId) PleosButtonStyle.Filled else PleosButtonStyle.Basic) { on(FleetUiAction.SelectDriver(d.id)) }
                    }
                }
                BasicText("현재 운행 기사: ${name(s.tripDriverId)}", style = t.bodyMedium.copy(color = c.textSecondary))
            }
            PleosCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
                    BasicText("공용 장비", style = t.titleSmall.copy(color = c.textPrimary))
                    PleosTag("샘플 차량")
                }
                s.equipment.forEach { e ->
                    BasicText("${e.name} · ${e.location}", style = t.bodyLarge.copy(color = if (e.status == "ok") c.textPrimary else c.negative))
                    Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xs)) {
                        STATUS.forEach { (k, label) ->
                            PleosButton(label, style = if (e.status == k) PleosButtonStyle.Filled else PleosButtonStyle.Basic) { on(FleetUiAction.SetEquipment(e.id, k)) }
                        }
                    }
                    e.updatedBy?.let { BasicText("마지막 수정: ${name(it)}", style = t.labelSmall.copy(color = c.textTertiary)) }
                }
            }
        }
        Column(Modifier.weight(0.5f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PleosSpacing.l)) {
            PleosCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
                    BasicText("인계 요약", style = t.titleSmall.copy(color = c.textPrimary))
                    PleosTag("규칙 기반 자동 요약")
                }
                s.preview?.lines?.forEach { BasicText("· $it", style = t.bodyMedium.copy(color = c.textPrimary)) }
            }
            val h = s.handover
            if (h != null && !h.accepted) {
                PleosCard {
                    BasicText("인수 대기: ${name(h.fromDriverId)} → ${name(h.toDriverId)}", style = t.titleSmall.copy(color = c.regulationOrange))
                    if (h.note.isNotBlank()) BasicText("전달 사항: ${h.note}", style = t.bodyMedium.copy(color = c.textPrimary))
                    h.lines.forEach { BasicText("· $it", style = t.labelMedium.copy(color = c.textSecondary)) }
                    if (s.driverId == h.toDriverId) PleosButton("내용 확인 · 인수 확정") { on(FleetUiAction.AcceptHandover) }
                    else BasicText("인수 기사(${name(h.toDriverId)})로 선택해야 인수할 수 있습니다", style = t.labelMedium.copy(color = c.textTertiary))
                }
            } else {
                PleosCard {
                    BasicText("기사 교대 인계", style = t.titleSmall.copy(color = c.textPrimary))
                    if (h?.accepted == true) BasicText("최근 인계: ${name(h.fromDriverId)} → ${name(h.toDriverId)} 인수 완료", style = t.labelMedium.copy(color = c.positive))
                    Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.s)) {
                        BasicText("인수 기사", style = t.bodyLarge.copy(color = c.textPrimary))
                        s.drivers.filter { it.id != s.driverId }.forEach { d ->
                            PleosButton(d.name, style = if (d.id == s.toDriverId) PleosButtonStyle.Filled else PleosButtonStyle.Basic) { on(FleetUiAction.ToDriver(d.id)) }
                        }
                    }
                    Field(s.note, "전달 사항 (예: C 주차 공간 좁음)", Modifier.fillMaxWidth()) { on(FleetUiAction.Note(it)) }
                    PleosButton("검토 완료 · 인계 확정", enabled = !s.busy && s.toDriverId != null && s.driverId != null) { on(FleetUiAction.CreateHandover) }
                    BasicText("공유 기록과 남은 업무만 넘어갑니다. 개인 메모는 포함되지 않습니다.", style = t.labelMedium.copy(color = c.textTertiary))
                }
            }
            s.message?.let { BasicText(it, style = t.bodyMedium.copy(color = c.textSecondary)) }
        }
    }
}
