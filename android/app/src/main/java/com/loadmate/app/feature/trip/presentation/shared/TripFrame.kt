package com.loadmate.app.feature.trip.presentation.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.shared.designsystem.components.PleosButton
import com.loadmate.app.shared.designsystem.components.PleosTitle
import com.loadmate.app.shared.designsystem.components.PleosToast
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.project.PleosCard
import com.loadmate.app.shared.designsystem.theme.Pleos
import com.loadmate.app.shared.format.localTime
import kotlinx.coroutines.delay
import java.time.Instant

/** 운행·적재 메뉴가 같이 쓰는 틀: 운행 이름·출처·예상 잔량, 오프라인 안내, 불러오기 실패, 일시 안내 */
@Composable
internal fun TripFrame(
    trip: Trip?,
    loading: Boolean,
    loadError: String?,
    offlineSince: Instant?,
    message: UiMessage?,
    on: (TripUiAction) -> Unit,
    overlays: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.(Trip) -> Unit,
) {
    val c = Pleos.colors
    val t = Pleos.type
    Box(Modifier.fillMaxSize().background(c.backgroundPrimary)) {
        Column(Modifier.fillMaxSize().padding(PleosSpacing.xl), verticalArrangement = Arrangement.spacedBy(PleosSpacing.l)) {
            PleosTitle(
                title = trip?.name.orEmpty(),
                suffix = {
                    trip?.let { BasicText(remainingHeadline(it), style = t.titleSmall.copy(color = c.textPrimary)) }
                },
            )
            offlineSince?.let {
                MessageBar(UiMessage("오프라인 · ${localTime(it.toString())} 기준 정보입니다", true)) {
                    on(TripUiAction.Refresh)
                }
            }
            when {
                trip != null -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(PleosSpacing.l),
                ) { content(trip) }
                loading -> BasicText("운행 정보를 불러오는 중…", style = t.bodyLarge.copy(color = c.textSecondary))
                else -> PleosCard(Modifier.widthIn(max = 960.dp)) {
                    BasicText("운행 정보를 불러오지 못했습니다", style = t.titleSmall.copy(color = c.negative))
                    BasicText(loadError ?: "", style = t.bodyMedium.copy(color = c.textSecondary))
                    PleosButton("다시 시도") { on(TripUiAction.Refresh) }
                }
            }
        }
        overlays()
        message?.let { m ->
            // 일반 안내는 자동으로 닫고, 오류는 기사가 확인할 때까지 남긴다
            LaunchedEffect(m) {
                if (!m.isError) {
                    delay(TOAST_MILLIS)
                    on(TripUiAction.DismissMessage)
                }
            }
            PleosToast(m.text, Modifier.align(Alignment.BottomCenter)) { on(TripUiAction.DismissMessage) }
        }
    }
}

private const val TOAST_MILLIS = 4_000L
