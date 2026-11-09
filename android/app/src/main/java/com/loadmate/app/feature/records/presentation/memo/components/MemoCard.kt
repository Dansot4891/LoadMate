package com.loadmate.app.feature.records.presentation.memo.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.loadmate.app.R
import com.loadmate.app.feature.records.domain.Memo
import com.loadmate.app.feature.records.presentation.memo.categoryLabel
import com.loadmate.app.shared.designsystem.components.PleosIconButton
import com.loadmate.app.shared.designsystem.components.PleosIconButtonType
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.project.PleosCard
import com.loadmate.app.shared.designsystem.project.PleosTag
import com.loadmate.app.shared.designsystem.theme.Pleos
import com.loadmate.app.shared.format.localTime

@Composable
internal fun MemoCard(memo: Memo, whereLabel: String?, busy: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val c = Pleos.colors
    val t = Pleos.type
    val vehicle = memo.scopeType == "vehicle"
    val issue = vehicle && memo.category == "issue"
    PleosCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.s)) {
            whereLabel?.let { PleosTag(it) }
            if (vehicle) PleosTag(categoryLabel(memo.category), if (issue) c.negative else c.textSecondary)
            if (!memo.shared) PleosTag("개인")
            if (memo.thisTripOnly) PleosTag("이번 운행")
            if (memo.source == "voice") PleosTag("음성")
            BasicText("수정 ${localTime(memo.updatedAt)}", Modifier.weight(1f), style = t.labelMedium.copy(color = c.textTertiary))
            if (memo.shared) {
                PleosIconButton(R.drawable.project_ic_edit, "수정", enabled = !busy, type = PleosIconButtonType.Plain, onClick = onEdit)
                PleosIconButton(R.drawable.pleos_ic_trash, "삭제", enabled = !busy, type = PleosIconButtonType.Plain, onClick = onDelete)
            }
        }
        BasicText(memo.body, style = t.bodyLarge.copy(color = if (issue) c.negative else c.textPrimary))
        memo.location?.let { BasicText("위치: $it", style = t.bodyMedium.copy(color = c.textSecondary)) }
    }
}
