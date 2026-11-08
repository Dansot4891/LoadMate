package com.loadmate.app.shared.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.loadmate.app.R
import com.loadmate.app.shared.designsystem.theme.Pleos
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing

/**
 * 화면 제목 줄. 공식 Figma ivi_title Main/Depth (높이 80, 간격 24, 제목 Bold 40/52).
 * 좌우 여백은 화면 바깥 여백을 그대로 쓴다.
 *
 * @param accessory 제목 바로 옆에 두는 표시 (예: 샘플 데이터 꼬리표)
 * @param suffix 오른쪽 끝 버튼
 */
@Composable
fun PleosTitle(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    accessory: @Composable RowScope.() -> Unit = {},
    suffix: @Composable RowScope.() -> Unit = {},
) {
    val c = Pleos.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 80.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xl),
    ) {
        onBack?.let {
            Box(Modifier.size(80.dp).clickable(role = Role.Button, onClickLabel = "뒤로", onClick = it), contentAlignment = Alignment.Center) {
                PleosIcon(R.drawable.pleos_ic_arrow_left, "뒤로", 56.dp, c.iconPrimary)
            }
        }
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PleosSpacing.xl),
        ) {
            BasicText(title, style = Pleos.type.titleLarge.copy(color = c.textPrimary))
            accessory()
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) { suffix() }
    }
}
