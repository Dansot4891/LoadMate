package com.loadmate.app.feature.settings.presentation.pleosstatus.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.loadmate.app.shared.designsystem.theme.Pleos
import com.loadmate.app.shared.designsystem.project.PleosCard
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.core.pleos.NaviConnection
import com.loadmate.app.core.pleos.RearDoorConnection
import com.loadmate.app.core.pleos.RearDoorState
import com.loadmate.app.core.pleos.SpeechConnection
import com.loadmate.app.core.pleos.SpeechState
import com.loadmate.app.feature.settings.presentation.pleosstatus.PleosStatusUiState
import com.loadmate.app.feature.settings.presentation.shared.StatusLine
import com.loadmate.app.feature.settings.presentation.shared.StatusRow

/** 기사가 "지금 되는지"만 보는 차량 기능 목록 */
@Composable
fun PleosStatusCard(state: PleosStatusUiState, notificationsAllowed: Boolean) {
    val c = Pleos.colors
    val t = Pleos.type
    PleosCard {
        BasicText("차량 기능", style = t.titleSmall.copy(color = c.textPrimary))
        listOf(
            naviLine(state.navi.connection),
            doorLine(state.rearDoor),
            speechLine(state.speech),
            ttsLine(state.ttsAvailable),
            notificationLine(notificationsAllowed),
        ).forEach { line ->
            Box(Modifier.fillMaxWidth().height(PleosShape.hairline).background(c.dividerTertiary))
            StatusRow(line)
        }
        BasicText(
            "차량 기능은 에뮬레이터에서만 확인했습니다. 실차 동작은 확인되지 않았습니다.",
            style = t.labelMedium.copy(color = c.textTertiary),
        )
    }
}

@Composable
private fun naviLine(conn: NaviConnection): StatusLine {
    val c = Pleos.colors
    return when (conn) {
        NaviConnection.Idle -> StatusLine("내비", "연결 전", c.textSecondary)
        NaviConnection.Connecting -> StatusLine("내비", "연결 중…", c.textSecondary)
        NaviConnection.Connected -> StatusLine("내비", "연결됨", c.positive)
        is NaviConnection.Failed -> StatusLine("내비", "연결 실패", c.negative, conn.reason)
    }
}

@Composable
private fun doorLine(s: RearDoorState): StatusLine {
    val c = Pleos.colors
    return when (val conn = s.connection) {
        RearDoorConnection.Stopped -> StatusLine("뒷문", "확인 안 함", c.textSecondary)
        is RearDoorConnection.Failed -> StatusLine("뒷문", "확인 실패", c.negative, conn.reason)
        RearDoorConnection.Subscribed -> when (s.isOpen) {
            true -> StatusLine("뒷문", "열림", c.positive)
            false -> StatusLine("뒷문", "닫힘", c.positive)
            null -> StatusLine("뒷문", "값 대기", c.textSecondary)
        }
    }
}

@Composable
private fun speechLine(s: SpeechState): StatusLine {
    val c = Pleos.colors
    return when (val conn = s.connection) {
        SpeechConnection.Idle -> StatusLine("음성 인식", "연결 전", c.textSecondary)
        SpeechConnection.Connecting -> StatusLine("음성 인식", "연결 중…", c.textSecondary)
        is SpeechConnection.Failed -> StatusLine("음성 인식", "사용 불가", c.negative, "${conn.reason} · 화면 입력으로 대체")
        SpeechConnection.Ready -> when {
            s.listening -> StatusLine("음성 인식", "듣는 중…", c.active)
            s.lastError != null -> StatusLine("음성 인식", "오류", c.negative, s.lastError)
            else -> StatusLine("음성 인식", "준비됨", c.positive)
        }
    }
}

@Composable
private fun ttsLine(available: Boolean?): StatusLine {
    val c = Pleos.colors
    return when (available) {
        null -> StatusLine("음성 출력", "아직 사용 전", c.textSecondary)
        true -> StatusLine("음성 출력", "사용 가능", c.positive)
        false -> StatusLine("음성 출력", "사용 불가", c.negative, "화면 안내로 대체")
    }
}

@Composable
private fun notificationLine(allowed: Boolean): StatusLine {
    val c = Pleos.colors
    return if (allowed) StatusLine("헤드업 알림", "권한 허용", c.positive)
    else StatusLine("헤드업 알림", "권한 없음", c.negative, "화면·음성 안내 사용")
}
