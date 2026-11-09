package com.loadmate.app.feature.trip.presentation.overview.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.presentation.shared.TripUiAction
import com.loadmate.app.feature.trip.presentation.shared.countsText
import com.loadmate.app.feature.trip.presentation.shared.departureLoad
import com.loadmate.app.feature.trip.presentation.shared.routeLoadGapText
import com.loadmate.app.shared.designsystem.components.PleosButtonStyle
import com.loadmate.app.shared.designsystem.components.PleosPopupAction
import com.loadmate.app.shared.designsystem.components.PleosSystemPopup
import com.loadmate.app.shared.designsystem.theme.Pleos

/** 출발 전 한 번만 묻는 적재 확인. 수량을 입력받지 않고, 수량의 출처를 그대로 밝힌다 (원칙 5) */
@Composable
internal fun DepartureDialog(trip: Trip, on: (TripUiAction) -> Unit) {
    val c = Pleos.colors
    val t = Pleos.type
    val load = departureLoad(trip)
    PleosSystemPopup(
        title = "현재 적재 상태를 확인해주세요.",
        onDismiss = { on(TripUiAction.DismissDeparture) },
        actions = listOf(
            PleosPopupAction("맞아요, 출발") { on(TripUiAction.ConfirmDeparture) },
            PleosPopupAction("아직이에요", PleosButtonStyle.Basic) { on(TripUiAction.DismissDeparture) },
        ),
    ) {
        BasicText(countsText(load.counts, trip), style = t.titleSmall.copy(color = c.textPrimary))
        routeLoadGapText(trip)?.let { BasicText("짐이 달라요 · $it", style = t.bodyMedium.copy(color = c.negative)) }
        BasicText(
            trip.stops.joinToString("\n") { "${it.label} ${it.name} · ${countsText(it.plannedCounts, trip)}" },
            style = t.bodyMedium.copy(color = c.textSecondary),
        )
        BasicText("${load.sourceLabel ?: "계획 수량"} 기준", style = t.labelMedium.copy(color = c.textTertiary))
    }
}
