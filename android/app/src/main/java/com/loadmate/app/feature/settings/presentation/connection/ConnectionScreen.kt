package com.loadmate.app.feature.settings.presentation.connection

import com.loadmate.app.shared.designsystem.components.PleosTitle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loadmate.app.shared.designsystem.theme.Pleos
import com.loadmate.app.shared.designsystem.components.PleosButton
import com.loadmate.app.shared.designsystem.components.PleosButtonSize
import com.loadmate.app.shared.designsystem.components.PleosButtonStyle
import com.loadmate.app.shared.designsystem.project.PleosCard
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.feature.settings.domain.DatabaseStatus
import com.loadmate.app.feature.settings.domain.ServerStatus
import com.loadmate.app.feature.settings.presentation.shared.StatusLine
import com.loadmate.app.feature.settings.presentation.shared.StatusRow

@Composable
fun ConnectionScreen(
    vm: ConnectionViewModel,
    status: @Composable () -> Unit,
) {
    // 화면에 들어오거나 내비에서 돌아올 때마다 자동으로 다시 확인한다
    LifecycleStartEffect(vm) {
        vm.onAction(ConnectionUiAction.Refresh)
        onStopOrDispose { }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    ConnectionContent(state, vm::onAction, status)
}

@Composable
private fun ConnectionContent(
    state: ConnectionUiState,
    onAction: (ConnectionUiAction) -> Unit,
    status: @Composable () -> Unit,
) {
    val c = Pleos.colors
    Box(
        Modifier.fillMaxSize().background(c.backgroundPrimary).verticalScroll(rememberScrollState()).padding(PleosSpacing.xl),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 960.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(PleosSpacing.l),
        ) {
            PleosTitle("연결 상태")
            PleosCard {
                val retry: (@Composable () -> Unit)? = if (canRetry(state.status)) {
                    {
                        PleosButton("다시 확인", enabled = !state.isChecking, style = PleosButtonStyle.Basic, size = PleosButtonSize.Small) {
                            onAction(ConnectionUiAction.Refresh)
                        }
                    }
                } else {
                    null
                }
                StatusRow(serverLine(state.status), retry)
            }
            status()
        }
    }
}

/** 연결이 정상이면 버튼을 숨기고, 다시 시도해 볼 의미가 있는 실패에서만 보여 준다 */
private fun canRetry(status: ServerStatus?): Boolean = when (status) {
    is ServerStatus.Offline -> true
    is ServerStatus.Online -> status.database != DatabaseStatus.OK
    ServerStatus.NotConfigured, null -> false
}

@Composable
private fun serverLine(status: ServerStatus?): StatusLine {
    val c = Pleos.colors
    return when (status) {
        null -> StatusLine("서버", "확인 중…", c.textSecondary)
        ServerStatus.NotConfigured -> StatusLine("서버", "미설정", c.negative, "release 빌드에 주소가 없습니다")
        is ServerStatus.Offline -> StatusLine("서버", "연결 안 됨", c.negative, status.reason)
        is ServerStatus.Online -> when (status.database) {
            DatabaseStatus.OK -> StatusLine("서버", "연결됨", c.positive)
            DatabaseStatus.UNAVAILABLE -> StatusLine("서버", "연결 안 됨", c.negative, "서버는 응답했지만 데이터베이스에 연결되지 않았습니다")
            DatabaseStatus.UNKNOWN -> StatusLine("서버", "확인 필요", c.textSecondary, "서버는 응답했지만 데이터베이스 상태를 알 수 없습니다")
        }
    }
}
