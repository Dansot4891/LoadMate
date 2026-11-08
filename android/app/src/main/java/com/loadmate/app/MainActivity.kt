package com.loadmate.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.loadmate.app.core.designsystem.Pleos
import com.loadmate.app.core.designsystem.PleosButton
import com.loadmate.app.core.designsystem.PleosButtonStyle
import com.loadmate.app.core.designsystem.PleosSpacing
import com.loadmate.app.core.designsystem.PleosShape
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

private val TABS = listOf("운행", "기록·메모", "장비·인계", "연결 상태")

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
            if (e == VoiceEffect.AskRoute) { tab = 0; drivingVm.onAction(com.loadmate.app.feature.trip.presentation.driving.DrivingUiAction.AskRoute) }
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
                PleosButton("운행 선택", style = PleosButtonStyle.Basic, onClick = onSelectTrip)
                TABS.forEachIndexed { i, name ->
                    PleosButton(name, style = if (tab == i) PleosButtonStyle.Filled else PleosButtonStyle.Basic) { tab = i }
                }
                Box(Modifier.weight(1f))
                val voiceColor = if (voice.alwaysListening) c.regulationGreen else c.regulationRed
                Row(
                    Modifier.heightIn(min = PleosShape.minTouch)
                        .background(c.surfaceLow, RoundedCornerShape(PleosShape.small))
                        .border(1.dp, voiceColor, RoundedCornerShape(PleosShape.small))
                        .clickable(role = Role.Button) { voiceVm.onAction(VoiceUiAction.ToggleAlwaysListening) }
                        .padding(horizontal = PleosSpacing.l),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PleosSpacing.s),
                ) {
                    Box(Modifier.size(12.dp).background(voiceColor, RoundedCornerShape(50)))
                    BasicText(
                        if (voice.alwaysListening) "음성 ON · 끄기" else "음성 OFF · 켜기",
                        style = t.bodyMedium.copy(color = voiceColor),
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> TripScreen(tripVm, loadCheckVm, drivingVm, siteWorkVm, mismatchVm)
                    1 -> RecordsScreen(viewModel(key = "records-$tripId", factory = RecordsViewModel.factory(container, tripId)))
                    2 -> FleetScreen(viewModel(key = "fleet-$tripId", factory = FleetViewModel.factory(container, tripId)))
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
