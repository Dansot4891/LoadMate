package com.loadmate.app.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loadmate.app.core.designsystem.Pleos
import com.loadmate.app.core.designsystem.PleosButton
import com.loadmate.app.core.designsystem.PleosShape
import com.loadmate.app.core.designsystem.PleosSpacing
import com.loadmate.app.core.pleos.NaviConnection
import com.loadmate.app.core.pleos.NaviEvent
import com.loadmate.app.core.pleos.RearDoorConnection
import com.loadmate.app.core.pleos.RearDoorState
import com.loadmate.app.core.pleos.SpeechConnection
import com.loadmate.app.core.pleos.SpeechState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PleosStatusSection(vm: PleosStatusViewModel) {
    // 화면이 보일 때만 SDK 리스너 유지 (CLAUDE.md 원칙 8)
    LifecycleStartEffect(vm) {
        vm.onAction(PleosStatusUiAction.Start)
        onStopOrDispose { vm.onAction(PleosStatusUiAction.Stop) }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    PleosStatusContent(state, vm::onAction)
}

@Composable
private fun PleosStatusContent(state: PleosStatusUiState, onAction: (PleosStatusUiAction) -> Unit) {
    val c = Pleos.colors
    val t = Pleos.type
    Column(
        modifier = Modifier
            .widthIn(max = 960.dp)
            .fillMaxWidth()
            .background(c.surfaceBasic, RoundedCornerShape(PleosShape.medium))
            .padding(PleosSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(PleosSpacing.m),
    ) {
        BasicText("Pleos 연동", style = t.titleSmall.copy(color = c.textPrimary))

        val (naviLabel, naviTone) = describeNavi(state.navi.connection)
        BasicText("내비(NaviHelper) · $naviLabel", style = t.bodyLarge.copy(color = naviTone))
        Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
            val connected = state.navi.connection == NaviConnection.Connected
            PleosButton("목적지 전달 (샘플)", enabled = connected) { onAction(PleosStatusUiAction.RequestSampleRoute) }
            PleosButton("경로 취소", enabled = connected) { onAction(PleosStatusUiAction.CancelRoute) }
        }
        BasicText(
            "샘플 목적지: ${state.sampleDestination.name} (실제 배송지 아님)",
            style = t.labelMedium.copy(color = c.textTertiary),
        )
        state.navi.events.take(5).forEach { e ->
            BasicText("${time(e.atMillis)}  ${describeEvent(e)}", style = t.labelMedium.copy(color = c.textSecondary))
        }

        val (doorLabel, doorTone) = describeDoor(state.rearDoor)
        BasicText("뒷문(Vehicle Door REAR) · $doorLabel", style = t.bodyLarge.copy(color = doorTone))
        BasicText(
            "에뮬레이터에서는 Virtual Console 신호입니다. 실차 동작은 확인되지 않았습니다.",
            style = t.labelMedium.copy(color = c.textTertiary),
        )

        val (sttLabel, sttTone) = describeSpeech(state.speech)
        BasicText("음성 인식(SpeechToText ON_DEVICE) · $sttLabel", style = t.bodyLarge.copy(color = sttTone))
        Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
            val ready = state.speech.connection == SpeechConnection.Ready
            PleosButton("듣기 시작", enabled = ready && !state.speech.listening) { onAction(PleosStatusUiAction.Listen) }
            PleosButton("듣기 중지", enabled = state.speech.listening) { onAction(PleosStatusUiAction.StopListening) }
        }
        (state.speech.finalText ?: state.speech.partial)?.let {
            BasicText("인식: $it", style = t.bodyMedium.copy(color = c.textPrimary))
        }
        if (state.speech.trace.isNotEmpty()) {
            BasicText(state.speech.trace.take(4).joinToString(" ← "), style = t.labelMedium.copy(color = c.textSecondary))
        }
    }
}

@Composable
private fun describeSpeech(s: SpeechState): Pair<String, Color> {
    val c = Pleos.colors
    return when (val conn = s.connection) {
        SpeechConnection.Idle -> "연결 전" to c.textSecondary
        SpeechConnection.Connecting -> "연결 중…" to c.textSecondary
        is SpeechConnection.Failed -> "사용 불가 · ${conn.reason} (화면 입력으로 대체)" to c.negative
        SpeechConnection.Ready -> when {
            s.listening -> "듣는 중…" to c.active
            s.lastError != null -> "오류 · ${s.lastError}" to c.negative
            else -> "준비됨" to c.positive
        }
    }
}

@Composable
private fun describeNavi(conn: NaviConnection): Pair<String, Color> {
    val c = Pleos.colors
    return when (conn) {
        NaviConnection.Idle -> "연결 전" to c.textSecondary
        NaviConnection.Connecting -> "연결 중…" to c.textSecondary
        NaviConnection.Connected -> "연결됨" to c.positive
        is NaviConnection.Failed -> "연결 실패 · ${conn.reason}" to c.negative
    }
}

@Composable
private fun describeDoor(s: RearDoorState): Pair<String, Color> {
    val c = Pleos.colors
    return when (val conn = s.connection) {
        RearDoorConnection.Stopped -> "구독 안 함" to c.textSecondary
        is RearDoorConnection.Failed -> "실패 · ${conn.reason}" to c.negative
        RearDoorConnection.Subscribed -> {
            val open = when (s.isOpen) { true -> "열림"; false -> "닫힘"; null -> "값 대기" }
            val range = if (s.minPosition != null && s.maxPosition != null) " (${s.minPosition}..${s.maxPosition})" else ""
            val at = s.updatedAtMillis?.let { " · ${time(it)}" } ?: ""
            "$open · 위치 ${s.position ?: "-"}$range · 알림 ${s.updateCount}회$at" to c.textPrimary
        }
    }
}

private fun describeEvent(e: NaviEvent): String = when (e) {
    is NaviEvent.RouteRequested -> "목적지 요청 전송 · ${e.destinationName}"
    is NaviEvent.RequestFailed -> "요청 실패 · ${e.reason}"
    is NaviEvent.RouteStarted -> "경로 안내 시작 · ${e.name} · ${e.distanceMeters}m · ${e.durationSeconds}s"
    is NaviEvent.RoutingCompleted -> "경로 탐색 완료"
    is NaviEvent.RouteCancelled -> "경로 취소됨"
    is NaviEvent.RouteEnded -> "경로 안내 종료"
    is NaviEvent.DestinationArrived -> "목적지 도착 · ${e.name}"
    is NaviEvent.StatusChanged -> "내비 상태 · ${e.status}"
    is NaviEvent.Error -> "내비 오류 · ${e.code}"
}

private fun time(millis: Long): String = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(millis))
