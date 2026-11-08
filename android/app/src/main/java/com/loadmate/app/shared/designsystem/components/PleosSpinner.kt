package com.loadmate.app.shared.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.loadmate.app.shared.designsystem.theme.Pleos

enum class PleosSpinnerSize(val height: Dp, val row: Dp) { Medium(288.dp, 96.dp), Large(384.dp, 88.dp) }

/** 한 열. Figma 기준 월·일·시·분 110, 연 200, 오전/오후 140 */
data class PleosSpinnerColumn(val items: List<String>, val selectedIndex: Int, val width: Dp, val onSelect: (Int) -> Unit)

/** 공식 Figma ivi_spinner (날짜·시각 휠, 모서리 32). 날짜는 너비 540, 시각은 480 */
@Composable
fun PleosSpinner(columns: List<PleosSpinnerColumn>, modifier: Modifier = Modifier, size: PleosSpinnerSize = PleosSpinnerSize.Large) {
    val c = Pleos.colors
    Box(
        modifier.height(size.height).background(c.backgroundPrimary, RoundedCornerShape(32.dp)).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(size.row).background(c.dimDarkPrimary, RoundedCornerShape(24.dp)))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            columns.forEach { WheelColumn(it, size) }
        }
    }
}

@Composable
private fun WheelColumn(column: PleosSpinnerColumn, size: PleosSpinnerSize) {
    val c = Pleos.colors
    val state = rememberLazyListState(column.selectedIndex)
    val current by remember { derivedStateOf { state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > 44) 1 else 0 } }
    val select by rememberUpdatedState(column.onSelect)
    LaunchedEffect(current, state.isScrollInProgress) {
        if (!state.isScrollInProgress && current != column.selectedIndex) select(current)
    }
    LazyColumn(
        Modifier.width(column.width).fillMaxHeight(),
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = PaddingValues(vertical = (size.height - size.row) / 2),
    ) {
        itemsIndexed(column.items) { i, text ->
            Box(Modifier.fillMaxWidth().height(size.row), contentAlignment = Alignment.Center) {
                BasicText(
                    text,
                    style = (if (i == current) Pleos.type.headlineLarge else Pleos.type.headlineSmall)
                        .copy(color = if (i == current) c.textPrimary else c.textQuaternary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                )
            }
        }
    }
}
