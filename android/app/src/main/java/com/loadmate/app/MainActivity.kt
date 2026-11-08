package com.loadmate.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loadmate.app.core.designsystem.Pleos
import com.loadmate.app.core.designsystem.PleosButton
import com.loadmate.app.core.designsystem.PleosButtonStyle
import com.loadmate.app.core.designsystem.PleosSpacing
import com.loadmate.app.core.designsystem.PleosTheme
import com.loadmate.app.feature.assistant.presentation.voice.VoiceEffect
import com.loadmate.app.feature.assistant.presentation.voice.VoicePanel
import com.loadmate.app.feature.assistant.presentation.voice.VoiceUiAction
import com.loadmate.app.feature.assistant.presentation.voice.VoiceViewModel
import com.loadmate.app.feature.fleet.presentation.equipment.FleetScreen
import com.loadmate.app.feature.fleet.presentation.equipment.FleetViewModel
import com.loadmate.app.feature.records.presentation.records.RecordsScreen
import com.loadmate.app.feature.records.presentation.records.RecordsViewModel
import com.loadmate.app.feature.settings.presentation.connection.ConnectionScreen
import com.loadmate.app.feature.settings.presentation.connection.ConnectionViewModel
import com.loadmate.app.feature.settings.presentation.pleosstatus.PleosStatusSection
import com.loadmate.app.feature.settings.presentation.pleosstatus.PleosStatusViewModel
import com.loadmate.app.feature.trip.presentation.TripScreen
import com.loadmate.app.feature.trip.presentation.TripUiAction
import com.loadmate.app.feature.trip.presentation.overview.OverviewViewModel
import com.loadmate.app.feature.trip.presentation.loadcheck.LoadCheckViewModel
import com.loadmate.app.feature.trip.presentation.driving.DrivingViewModel
import com.loadmate.app.feature.trip.presentation.sitework.SiteWorkViewModel
import com.loadmate.app.feature.trip.presentation.mismatch.MismatchViewModel

private val TABS = listOf("운행", "기록·메모", "장비·인계", "연결 상태")

class MainActivity : ComponentActivity() {
    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 내비 위 헤드업 알림 (F32). 거부해도 화면·음성 안내는 동작한다
        if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        val container = (application as LoadmateApplication).container
        val tripId = container.sampleTripId
        setContent {
            PleosTheme {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                val tripVm: OverviewViewModel = viewModel(factory = OverviewViewModel.factory(container))
                val loadCheckVm: LoadCheckViewModel = viewModel(factory = LoadCheckViewModel.factory(container))
                val drivingVm: DrivingViewModel = viewModel(factory = DrivingViewModel.factory(container))
                val siteWorkVm: SiteWorkViewModel = viewModel(factory = SiteWorkViewModel.factory(container))
                val mismatchVm: MismatchViewModel = viewModel(factory = MismatchViewModel.factory(container))
                val voiceVm: VoiceViewModel = viewModel(factory = VoiceViewModel.factory(container, tripId))
                val voice by voiceVm.state.collectAsStateWithLifecycle()
                val driverId by container.driverStore.driverId.collectAsState()
                val driverName by container.driverStore.driverName.collectAsState()
                LaunchedEffect(voiceVm) {
                    voiceVm.effect.collect { e ->
                        if (e == VoiceEffect.AskRoute) { tab = 0; drivingVm.onAction(TripUiAction.AskRoute) }
                    }
                }
                val c = Pleos.colors
                val t = Pleos.type
                Box(Modifier.fillMaxSize().background(c.backgroundPrimary)) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.padding(start = PleosSpacing.xl, end = PleosSpacing.xl, top = PleosSpacing.l),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m),
                        ) {
                            BasicText("Loadmate", style = t.titleMedium.copy(color = c.textPrimary))
                            TABS.forEachIndexed { i, name ->
                                PleosButton(name, style = if (tab == i) PleosButtonStyle.Filled else PleosButtonStyle.Basic) { tab = i }
                            }
                            Box(Modifier.weight(1f))
                            BasicText(driverId?.let { "기사: ${driverName ?: it}" } ?: "기사 미선택 · 장비·인계에서 선택", style = t.bodyMedium.copy(color = if (driverId == null) c.negative else c.textSecondary))
                            PleosButton("🎤 말하기") { voiceVm.onAction(VoiceUiAction.Open) }
                        }
                        Box(Modifier.weight(1f)) {
                            when (tab) {
                                0 -> TripScreen(tripVm, loadCheckVm, drivingVm, siteWorkVm, mismatchVm)
                                1 -> RecordsScreen(viewModel(factory = RecordsViewModel.factory(container, tripId)))
                                2 -> FleetScreen(viewModel(factory = FleetViewModel.factory(container, tripId)))
                                else -> {
                                    val vm: ConnectionViewModel = viewModel(factory = ConnectionViewModel.factory(container))
                                    val pleosVm: PleosStatusViewModel = viewModel(factory = PleosStatusViewModel.factory(container))
                                    ConnectionScreen(vm, onBack = null) { PleosStatusSection(pleosVm) }
                                }
                            }
                        }
                    }
                    VoicePanel(voice, voiceVm::onAction)
                }
            }
        }
    }
}
