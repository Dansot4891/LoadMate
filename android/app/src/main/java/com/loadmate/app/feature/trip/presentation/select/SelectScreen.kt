package com.loadmate.app.feature.trip.presentation.select

import com.loadmate.app.shared.designsystem.components.PleosTitle
import com.loadmate.app.shared.designsystem.components.PleosButtonStyle
import com.loadmate.app.shared.designsystem.project.PleosCard
import com.loadmate.app.feature.trip.presentation.shared.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loadmate.app.shared.designsystem.theme.Pleos
import com.loadmate.app.shared.designsystem.components.PleosButton
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.feature.trip.presentation.select.components.TripRow

@Composable
fun SelectScreen(viewModel: SelectViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Pleos.colors.backgroundPrimary).verticalScroll(rememberScrollState()).padding(PleosSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(PleosSpacing.l)) {
        PleosTitle(
            title = "오늘의 운행",
            suffix = { PleosButton("새로고침", style = PleosButtonStyle.Basic, enabled = !state.loading) { viewModel.onAction(SelectUiAction.Refresh) } },
        )
        if (state.loading) BasicText("운행 목록을 불러오는 중…", style = Pleos.type.bodyLarge.copy(color = Pleos.colors.textSecondary))
        state.error?.let { BasicText(it, style = Pleos.type.bodyLarge.copy(color = Pleos.colors.negative)) }
        if (!state.loading && state.trips.isEmpty() && state.error == null) {
            BasicText("표시할 운행이 없습니다", style = Pleos.type.bodyLarge.copy(color = Pleos.colors.textSecondary))
        }
        if (state.trips.isNotEmpty()) PleosCard {
            state.trips.forEach { trip -> TripRow(trip) { viewModel.onAction(SelectUiAction.Choose(trip.id)) } }
        }
    }
}
