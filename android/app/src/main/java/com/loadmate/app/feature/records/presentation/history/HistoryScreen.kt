package com.loadmate.app.feature.records.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loadmate.app.feature.records.presentation.history.components.HistoryCard
import com.loadmate.app.shared.designsystem.components.PleosDropdown
import com.loadmate.app.shared.designsystem.components.PleosDropdownSize
import com.loadmate.app.shared.designsystem.components.PleosToast
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.theme.Pleos

@Composable
fun HistoryScreen(vm: HistoryViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.onAction(HistoryUiAction.Refresh) }
    val on = vm::onAction
    val c = Pleos.colors
    val t = Pleos.type
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(PleosSpacing.xl).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("내역", Modifier.weight(1f), style = t.titleMedium.copy(color = c.textPrimary))
                PleosDropdown(
                    options = HistoryFilter.entries,
                    selected = s.filter,
                    label = { it.label },
                    placeholder = HistoryFilter.ALL.label,
                    modifier = Modifier.width(320.dp),
                    size = PleosDropdownSize.Small,
                ) { on(HistoryUiAction.SelectFilter(it)) }
            }
            if (s.shown.isEmpty()) BasicText("내역이 없습니다", style = t.bodyMedium.copy(color = c.textTertiary))
            s.shown.forEach { HistoryCard(it) }
        }
        s.message?.let { m ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                PleosToast(m) { on(HistoryUiAction.DismissMessage) }
            }
        }
    }
}
