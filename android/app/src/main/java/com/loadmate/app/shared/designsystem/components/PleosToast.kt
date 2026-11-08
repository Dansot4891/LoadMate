package com.loadmate.app.shared.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loadmate.app.shared.designsystem.theme.Pleos
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing

/**
 * 공식 Figma ivi_toast_popup (최대 너비 792, 모서리 16, 글자 Bold 26/34).
 * 배경·글자는 Figma의 basic/600·basic/00 과 같은 값인 filled 버튼 토큰을 쓴다.
 * 누르면 닫는다. 언제 자동으로 닫을지는 호출하는 화면이 정한다.
 */
@Composable
fun PleosToast(message: String, modifier: Modifier = Modifier, onDismiss: () -> Unit) {
    val c = Pleos.colors
    BasicText(
        message,
        modifier
            .padding(PleosSpacing.toastPadding)
            .widthIn(max = PleosSpacing.toastMaxWidth)
            .background(c.buttonFilledEnabled, RoundedCornerShape(PleosShape.medium))
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onDismiss)
            .padding(horizontal = 40.dp, vertical = PleosSpacing.xl),
        style = Pleos.type.bodySmall.copy(color = c.onFilled, fontWeight = FontWeight.Bold),
    )
}
