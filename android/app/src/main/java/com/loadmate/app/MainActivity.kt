package com.loadmate.app

import com.loadmate.app.shared.designsystem.components.PleosSwitch
import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loadmate.app.shared.designsystem.theme.Pleos
import com.loadmate.app.shared.designsystem.project.PleosRailItem
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.theme.PleosTheme
import com.loadmate.app.feature.assistant.presentation.voice.VoiceEffect
import com.loadmate.app.feature.assistant.presentation.voice.VoicePanel
import com.loadmate.app.feature.assistant.presentation.voice.VoiceUiAction
import com.loadmate.app.feature.assistant.presentation.voice.VoiceViewModel
import com.loadmate.app.feature.records.presentation.history.HistoryScreen
import com.loadmate.app.feature.records.presentation.history.HistoryViewModel
import com.loadmate.app.feature.records.presentation.memo.MemoScreen
import com.loadmate.app.feature.records.presentation.memo.MemoViewModel
import com.loadmate.app.feature.settings.presentation.connection.ConnectionScreen
import com.loadmate.app.feature.settings.presentation.connection.ConnectionViewModel
import com.loadmate.app.feature.settings.presentation.pleosstatus.PleosStatusSection
import com.loadmate.app.feature.settings.presentation.pleosstatus.PleosStatusViewModel
import com.loadmate.app.feature.trip.presentation.select.SelectScreen
import com.loadmate.app.feature.trip.presentation.select.SelectViewModel
import com.loadmate.app.feature.trip.presentation.select.SelectUiEffect
import com.loadmate.app.feature.trip.presentation.shared.TripScreen
import com.loadmate.app.feature.trip.presentation.shared.TripUiAction
import com.loadmate.app.feature.trip.presentation.overview.OverviewViewModel
import com.loadmate.app.feature.trip.presentation.loadcheck.LoadCheckViewModel
import com.loadmate.app.feature.trip.presentation.driving.DrivingViewModel
import com.loadmate.app.feature.trip.presentation.sitework.SiteWorkViewModel
import com.loadmate.app.feature.trip.presentation.mismatch.MismatchViewModel

private class TabItem(val label: String, @DrawableRes val icon: Int)

private val TABS = listOf(
    TabItem("운행", R.drawable.pleos_ic_vehicle),
    TabItem("메모", R.drawable.ic_nav_records),
    TabItem("내역", R.drawable.pleos_ic_menu_3),
    TabItem("연결 상태", R.drawable.pleos_ic_info_line),
)

/** 왼쪽 세로 메뉴 너비 (PROJECT 값) */
private val RAIL_WIDTH = 168.dp

class MainActivity : ComponentActivity() {
    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 내비 위 헤드업 알림 (F32). 거부해도 화면·음성 안내는 동작한다
        if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        val container = (application as LoadmateApplication).container
        setContent {
            PleosTheme {
                var selectedTripId by rememberSaveable { mutableStateOf<String?>(null) }
                val selectVm: SelectViewModel = viewModel(factory = SelectViewModel.factory(container))
                LaunchedEffect(selectVm) {
                    selectVm.effect.collect { effect ->
                        if (effect is SelectUiEffect.OpenTrip) selectedTripId = effect.tripId
                    }
                }
                val tripId = selectedTripId
                if (tripId == null) {
                    SelectScreen(selectVm)
                } else key(tripId) {
                    val store = remember(tripId) { ViewModelStore() }
                    DisposableEffect(tripId) {
                        onDispose {
                            store.clear()
                            container.releaseTripCoordinator(tripId)
                        }
                    }
                    val owner = remember(store) { object : ViewModelStoreOwner { override val viewModelStore = store } }
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                        TripTabs(container, tripId, onSelectTrip = { selectedTripId = null })
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as LoadmateApplication).container.appVisible.value = true
    }

    override fun onStop() {
        (application as LoadmateApplication).container.appVisible.value = false
        super.onStop()
    }
}

@androidx.compose.runtime.Composable
private fun TripTabs(container: AppContainer, tripId: String, onSelectTrip: () -> Unit) {
    var tab by rememberSaveable(tripId) { mutableIntStateOf(0) }
    val tripVm: OverviewViewModel = viewModel(key = "overview-$tripId", factory = OverviewViewModel.factory(container, tripId))
    val loadCheckVm: LoadCheckViewModel = viewModel(key = "loadcheck-$tripId", factory = LoadCheckViewModel.factory(container, tripId))
    val drivingVm: DrivingViewModel = viewModel(key = "driving-$tripId", factory = DrivingViewModel.factory(container, tripId))
    val siteWorkVm: SiteWorkViewModel = viewModel(key = "sitework-$tripId", factory = SiteWorkViewModel.factory(container, tripId))
    val mismatchVm: MismatchViewModel = viewModel(key = "mismatch-$tripId", factory = MismatchViewModel.factory(container, tripId))
    val voiceVm: VoiceViewModel = viewModel(key = "voice-$tripId", factory = VoiceViewModel.factory(container, tripId))
    val voice by voiceVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(voiceVm) {
        voiceVm.effect.collect { e ->
            when (e) {
                VoiceEffect.AskRoute -> { tab = 0; drivingVm.onAction(com.loadmate.app.feature.trip.presentation.driving.DrivingUiAction.AskRoute) }
                VoiceEffect.Arrived -> container.tripSession.markArrived()
            }
        }
    }
    val c = Pleos.colors
    val t = Pleos.type
    Box(Modifier.fillMaxSize().background(c.backgroundPrimary)) {
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.width(RAIL_WIDTH).fillMaxHeight().background(c.backgroundSecondary).padding(PleosSpacing.m),
                verticalArrangement = Arrangement.spacedBy(PleosSpacing.s),
            ) {
                PleosRailItem("운행 목록", R.drawable.pleos_ic_arrow_left, selected = false, role = Role.Button, onClick = onSelectTrip)
                Box(Modifier.fillMaxWidth().height(PleosShape.hairline).background(c.divider))
                TABS.forEachIndexed { i, item ->
                    PleosRailItem(item.label, item.icon, selected = tab == i) { tab = i }
                }
                Box(Modifier.weight(1f))
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PleosSpacing.xs),
                ) {
                    BasicText(if (voice.alwaysListening) "음성 ON" else "음성 OFF", style = t.bodySmall.copy(color = c.textPrimary))
                    PleosSwitch(voice.alwaysListening, { voiceVm.onAction(VoiceUiAction.ToggleAlwaysListening) })
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (tab) {
                    0 -> TripScreen(tripVm, loadCheckVm, drivingVm, siteWorkVm, mismatchVm)
                    1 -> MemoScreen(viewModel(key = "memo-$tripId", factory = MemoViewModel.factory(container, tripId)))
                    2 -> HistoryScreen(viewModel(key = "history-$tripId", factory = HistoryViewModel.factory(container, tripId)))
                    else -> {
                        val vm: ConnectionViewModel = viewModel(factory = ConnectionViewModel.factory(container))
                        val pleosVm: PleosStatusViewModel = viewModel(factory = PleosStatusViewModel.factory(container))
                        ConnectionScreen(vm, status = { PleosStatusSection(pleosVm) })
                    }
                }
            }
        }
        VoicePanel(voice, voiceVm::onAction)
    }
}
