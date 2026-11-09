package com.loadmate.app.feature.trip.presentation.select.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.loadmate.app.R
import com.loadmate.app.shared.designsystem.theme.Pleos
import com.loadmate.app.feature.trip.domain.TripStatus
import com.loadmate.app.feature.trip.domain.TripSummary
import com.loadmate.app.shared.designsystem.components.PleosIcon
import com.loadmate.app.shared.designsystem.components.PleosListItem

@Composable
fun TripRow(trip: TripSummary, onChoose: () -> Unit) {
    PleosListItem(
        title = trip.name,
        subtitle = "방문지 ${trip.stopCount}곳 · ${statusText(trip.status)}",
        onClick = onChoose,
        trailing = {
            PleosIcon(R.drawable.pleos_ic_arrow_right, null, 40.dp, Pleos.colors.iconSecondary)
        },
    )
}

internal fun statusText(status: TripStatus): String = when (status) {
    TripStatus.PLANNED -> "예정"
    TripStatus.IN_PROGRESS -> "진행 중"
    TripStatus.COMPLETED -> "완료"
    TripStatus.ENDED_INCOMPLETE -> "미완료 종료"
    TripStatus.UNKNOWN -> "상태 확인 필요"
}
