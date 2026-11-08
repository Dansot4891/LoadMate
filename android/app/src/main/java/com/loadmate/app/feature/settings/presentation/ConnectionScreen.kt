package com.loadmate.app.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loadmate.app.core.designsystem.Pleos
import com.loadmate.app.core.designsystem.PleosButton
import com.loadmate.app.core.designsystem.PleosButtonStyle
import com.loadmate.app.core.designsystem.PleosShape
import com.loadmate.app.core.designsystem.PleosSpacing
import com.loadmate.app.feature.settings.domain.DatabaseStatus
import com.loadmate.app.feature.settings.domain.ServerStatus

@Composable
fun ConnectionScreen(vm: ConnectionViewModel, onBack: () -> Unit, pleosSection: @Composable () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    ConnectionContent(state, vm::onAction, onBack, pleosSection)
}

@Composable
private fun ConnectionContent(
    state: ConnectionUiState,
    onAction: (ConnectionUiAction) -> Unit,
    onBack: () -> Unit,
    pleosSection: @Composable () -> Unit,
) {
    val c = Pleos.colors
    val t = Pleos.type
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.backgroundPrimary)
            .verticalScroll(rememberScrollState())
            .padding(PleosSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PleosSpacing.xl),
    ) {
        PleosButton("← 운행 화면", style = PleosButtonStyle.Basic, onClick = onBack)
        BasicText("연결 상태", style = t.headlineSmall.copy(color = c.textPrimary))
        BasicText("서버 · Pleos SDK 연결 확인 (S10)", style = t.bodyMedium.copy(color = c.textSecondary))

        Column(
            modifier = Modifier
                .widthIn(max = 960.dp)
                .fillMaxWidth()
                .background(c.surfaceBasic, RoundedCornerShape(PleosShape.medium))
                .padding(PleosSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(PleosSpacing.m),
        ) {
            BasicText("서버 연결", style = t.titleSmall.copy(color = c.textPrimary))
            BasicText(
                state.baseUrl.ifBlank { "(주소 없음)" },
                style = t.labelMedium.copy(color = c.textTertiary),
            )
            val (label, tone) = describe(state)
            BasicText(label, style = t.bodyLarge.copy(color = tone))
        }

        PleosButton("다시 확인", enabled = !state.isChecking) { onAction(ConnectionUiAction.Refresh) }

        pleosSection()
    }
}

@Composable
private fun describe(state: ConnectionUiState): Pair<String, Color> {
    val c = Pleos.colors
    if (state.isChecking) return "확인 중…" to c.textSecondary
    return when (val s = state.status) {
        null -> "확인 전" to c.textSecondary
        ServerStatus.NotConfigured -> "서버 미설정 (release 빌드에 주소가 없습니다)" to c.negative
        is ServerStatus.Offline -> "오프라인 · ${s.reason}" to c.negative
        is ServerStatus.Online -> when (s.database) {
            DatabaseStatus.OK -> "정상 · 서버 ${s.serverVersion} · DB 연결됨" to c.positive
            DatabaseStatus.UNAVAILABLE -> "서버 응답 · DB 연결 안 됨" to c.negative
            DatabaseStatus.UNKNOWN -> "서버 응답 · DB 상태 알 수 없음" to c.textSecondary
        }
    }
}
