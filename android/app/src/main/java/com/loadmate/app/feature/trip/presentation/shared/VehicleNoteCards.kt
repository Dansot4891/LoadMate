package com.loadmate.app.feature.trip.presentation.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.loadmate.app.feature.records.domain.Memo
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.project.PleosCard
import com.loadmate.app.shared.designsystem.theme.Pleos

private fun Memo.line(): String = body + (location?.let { " · $it" } ?: "")

private const val START_CHECK_MAX = 2

/** 운행 시작 카드에 보일 메모: 출발 전에 알아야 할 차량 문제만. 나머지는 메모 메뉴에서 본다 */
internal fun startCheckNotes(notes: List<Memo>): List<Memo> = notes.filter { it.category == "issue" }

/** 도착 카드에 보일 도구 위치 */
internal fun arrivalToolNotes(notes: List<Memo>): List<Memo> = notes.filter { it.category == "tool" }

/** 운행 시작 전에 누가 타든 보는 차량 확인 카드 */
@Composable
internal fun VehicleCheckCard(notes: List<Memo>) {
    val items = startCheckNotes(notes)
    if (items.isEmpty()) return
    val c = Pleos.colors
    val t = Pleos.type
    PleosCard {
        items.take(START_CHECK_MAX).forEach { n ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.l)) {
                BasicText("⚠ ${n.body}", Modifier.weight(1f), style = t.bodyLarge.copy(color = c.negative))
                n.location?.let { BasicText(it, style = t.bodyMedium.copy(color = c.textSecondary)) }
            }
        }
        val more = items.size - START_CHECK_MAX
        if (more > 0) BasicText("외 ${more}건", style = t.labelMedium.copy(color = c.textTertiary))
    }
}

/** 납품처에 도착했을 때 그 장소 메모와 도구 위치를 함께 보여 준다 */
@Composable
internal fun ArrivalNotesCard(stopMemos: List<Memo>, vehicleNotes: List<Memo>) {
    val tools = arrivalToolNotes(vehicleNotes)
    if (stopMemos.isEmpty() && tools.isEmpty()) return
    val c = Pleos.colors
    val t = Pleos.type
    PleosCard {
        if (stopMemos.isNotEmpty()) {
            BasicText("이 장소 메모 (${stopMemos.size})", style = t.titleSmall.copy(color = c.textPrimary))
            stopMemos.take(3).forEach { BasicText(it.body, style = t.bodyMedium.copy(color = c.textPrimary)) }
        }
        if (tools.isNotEmpty()) {
            BasicText("도구 위치", style = t.titleSmall.copy(color = c.textPrimary))
            tools.take(4).forEach { BasicText(it.line(), style = t.bodyMedium.copy(color = c.textSecondary)) }
        }
    }
}
