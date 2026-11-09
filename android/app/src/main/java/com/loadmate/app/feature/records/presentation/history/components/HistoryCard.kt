package com.loadmate.app.feature.records.presentation.history.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.loadmate.app.feature.records.presentation.history.historyMeta
import com.loadmate.app.feature.trip.domain.HistoryItem
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.project.PleosCard
import com.loadmate.app.shared.designsystem.theme.Pleos

@Composable
internal fun HistoryCard(item: HistoryItem) {
    val c = Pleos.colors
    val t = Pleos.type
    PleosCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.l)) {
            BasicText(item.summary, Modifier.weight(1f), style = t.bodyLarge.copy(color = c.textPrimary))
            BasicText(historyMeta(item), style = t.labelMedium.copy(color = c.textTertiary))
        }
    }
}
