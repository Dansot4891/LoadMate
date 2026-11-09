package com.loadmate.app.feature.trip.presentation.cargo.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.loadmate.app.core.pleos.RearDoorState
import com.loadmate.app.feature.trip.presentation.cargo.rearDoorText
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.project.PleosCard
import com.loadmate.app.shared.designsystem.theme.Pleos
import com.loadmate.app.shared.format.localTime
import java.time.Instant

/** 뒷문 상태와 운행 중 뒷문 경고 기록. 뒷문 신호는 근거일 뿐 하차 완료를 뜻하지 않는다 (원칙 2) */
@Composable
internal fun RearDoorStatusCard(door: RearDoorState, warningAt: Long?) {
    val text = rearDoorText(door) ?: return
    val c = Pleos.colors
    val t = Pleos.type
    PleosCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xl)) {
            BasicText(text, style = t.titleMedium.copy(color = if (door.isOpen == true) c.negative else c.textPrimary))
            warningAt?.let {
                BasicText("운행 중 뒷문 열림 · ${localTime(Instant.ofEpochMilli(it).toString())}", style = t.bodyLarge.copy(color = c.negative))
            }
        }
    }
}
