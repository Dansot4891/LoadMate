package com.loadmate.app.feature.trip.presentation.cargo.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.loadmate.app.feature.trip.domain.CargoUnit
import kotlinx.coroutines.delay

private const val UNLOAD_STEP_MS = 1200
private const val UNLOAD_GAP_MS = 250L
private const val UNLOAD_HOLD_MS = 900L
private const val LIFT_END = 0.3f
private const val FADE_START = 0.8f

/** 한 상자가 빠지는 진행도(0~1)를 들어 올림·밀어내기·사라짐 정도로 나눈다 */
internal fun unloadLiftAmount(progress: Float) = (progress / LIFT_END).coerceIn(0f, 1f)
internal fun unloadSlideAmount(progress: Float) = ((progress - LIFT_END) / (1f - LIFT_END)).coerceIn(0f, 1f)
internal fun unloadAlpha(progress: Float) = 1f - ((progress - FADE_START) / (1f - FADE_START)).coerceIn(0f, 1f)

/** 꺼내는 순서 미리 보기의 화면 상태. 서버에 기록하지 않으며 하차 완료와 무관하다 (원칙 2) */
@Stable
internal class UnloadPreviewState {
    var playing by mutableStateOf(false)
    var activeId by mutableStateOf<Int?>(null)
        internal set
    var gone by mutableStateOf(emptySet<Int>())
        internal set
    internal val animation = Animatable(0f)
    val progress: Float get() = animation.value
}

@Composable
internal fun rememberUnloadPreview(order: List<CargoUnit>): UnloadPreviewState {
    val state = remember { UnloadPreviewState() }
    val latestOrder by rememberUpdatedState(order)
    LaunchedEffect(state.playing) {
        state.activeId = null
        state.gone = emptySet()
        if (!state.playing) return@LaunchedEffect
        latestOrder.toList().forEach { unit ->
            state.animation.snapTo(0f)
            state.activeId = unit.id
            state.animation.animateTo(1f, tween(UNLOAD_STEP_MS, easing = FastOutSlowInEasing))
            state.gone = state.gone + unit.id
            state.activeId = null
            delay(UNLOAD_GAP_MS)
        }
        delay(UNLOAD_HOLD_MS)
        state.playing = false
    }
    return state
}
