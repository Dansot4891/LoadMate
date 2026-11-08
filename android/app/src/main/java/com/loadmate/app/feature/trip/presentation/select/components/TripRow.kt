package com.loadmate.app.feature.trip.presentation.select.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.loadmate.app.core.designsystem.Pleos
import com.loadmate.app.core.designsystem.PleosButton
import com.loadmate.app.core.designsystem.PleosCard
import com.loadmate.app.core.designsystem.PleosSpacing
import com.loadmate.app.core.designsystem.PleosTag
import com.loadmate.app.feature.trip.domain.TripStatus
import com.loadmate.app.feature.trip.domain.TripSummary

@Composable
fun TripRow(trip: TripSummary, onChoose: () -> Unit) {
    PleosCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PleosSpacing.l), verticalAlignment = Alignment.CenterVertically) {
            BasicText(trip.name, modifier = Modifier.weight(1f), style = Pleos.type.titleSmall.copy(color = Pleos.colors.textPrimary))
            if (trip.dataSource == "sample") PleosTag("샘플 데이터")
            BasicText("방문지 ${trip.stopCount}곳 · ${statusText(trip.status)}", style = Pleos.type.bodyMedium.copy(color = Pleos.colors.textSecondary))
            PleosButton("운행 열기", onClick = onChoose)
        }
    }
}

internal fun statusText(status: TripStatus): String = when (status) {
    TripStatus.PLANNED -> "예정"
    TripStatus.IN_PROGRESS -> "진행 중"
    TripStatus.COMPLETED -> "완료"
    TripStatus.ENDED_INCOMPLETE -> "미완료 종료"
    TripStatus.UNKNOWN -> "상태 확인 필요"
}
