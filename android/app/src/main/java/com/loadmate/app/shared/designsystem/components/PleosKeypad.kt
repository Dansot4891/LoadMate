package com.loadmate.app.shared.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.loadmate.app.R
import com.loadmate.app.shared.designsystem.theme.Pleos

/** 공식 Figma ivi_numeric_keypad (키 200×108, 모서리 32, 테두리 4) */
@Composable
fun PleosNumericKeypad(onDigit: (Int) -> Unit, onDelete: () -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier, clearLabel: String = "Clear") {
    val c = Pleos.colors
    val t = Pleos.type
    val digit = t.headlineMedium.copy(color = c.textPrimary)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { n -> NumberKey(c.keyboardPrimaryNormal, c.keyboardPrimaryPressed, { onDigit(n) }) { BasicText("$n", style = digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberKey(c.surfaceAccent, c.surfaceHigh, onClear) { BasicText(clearLabel, style = t.titleLarge.copy(color = c.textPrimary)) }
            NumberKey(c.keyboardPrimaryNormal, c.keyboardPrimaryPressed, { onDigit(0) }) { BasicText("0", style = digit) }
            NumberKey(c.surfaceAccent, c.surfaceHigh, onDelete) { PleosIcon(R.drawable.pleos_ic_close, "지우기", 48.dp, c.iconPrimary) }
        }
    }
}

@Composable
private fun NumberKey(normal: Color, pressedColor: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = Pleos.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(32.dp)
    Box(
        Modifier.size(200.dp, 108.dp).background(if (pressed) pressedColor else normal, shape).border(4.dp, c.divider, shape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

enum class PleosKeyKind(val width: Dp) {
    Primary(100.dp), Secondary(120.dp), Hyphen(120.dp), Mic(120.dp), Delete(120.dp), Shift(165.dp), Enter(200.dp), Space(300.dp),
}

/** 공식 Figma ivi_keypad_normal / ivi_keypad_static 키 한 개 (높이 80, 모서리 20). 배열은 입력기마다 달라 호출부가 정한다 */
@Composable
fun PleosKeyboardKey(
    kind: PleosKeyKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    @DrawableRes icon: Int? = null,
    static: Boolean = false,
    active: Boolean = false,
) {
    val c = Pleos.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = c.keyColor(kind, static, active, pressed)
    val fg = when {
        !static && (kind == PleosKeyKind.Enter || active) -> c.onFilled
        static && kind == PleosKeyKind.Enter -> c.keyboardStaticIconDark
        static -> c.keyboardStaticText
        else -> c.textPrimary
    }
    val style: TextStyle = when (kind) {
        PleosKeyKind.Space -> Pleos.type.titleSmall.copy(fontWeight = FontWeight.Normal)
        PleosKeyKind.Secondary -> Pleos.type.titleMedium.copy(fontWeight = FontWeight.Normal)
        else -> Pleos.type.titleLarge.copy(fontWeight = FontWeight.Normal)
    }
    Box(
        modifier.widthIn(min = kind.width).heightIn(min = 80.dp).background(bg, RoundedCornerShape(20.dp))
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) PleosIcon(icon, label, 44.dp, fg)
        else BasicText(label.orEmpty(), style = style.copy(color = fg))
    }
}
