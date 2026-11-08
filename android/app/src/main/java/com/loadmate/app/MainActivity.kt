package com.loadmate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loadmate.app.core.designsystem.PleosTheme
import com.loadmate.app.feature.settings.presentation.ConnectionScreen
import com.loadmate.app.feature.settings.presentation.ConnectionViewModel
import com.loadmate.app.feature.settings.presentation.PleosStatusSection
import com.loadmate.app.feature.settings.presentation.PleosStatusViewModel
import com.loadmate.app.feature.trip.presentation.TripScreen
import com.loadmate.app.feature.trip.presentation.TripViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as LoadmateApplication).container
        setContent {
            PleosTheme {
                // 화면 전환이 2개뿐이라 내비게이션 라이브러리 없이 상태로 전환한다
                var showSettings by rememberSaveable { mutableStateOf(false) }
                val tripVm: TripViewModel = viewModel(factory = TripViewModel.factory(container, container.sampleTripId))
                if (showSettings) {
                    BackHandler { showSettings = false }
                    val vm: ConnectionViewModel = viewModel(factory = ConnectionViewModel.factory(container))
                    val pleosVm: PleosStatusViewModel = viewModel(factory = PleosStatusViewModel.factory(container))
                    ConnectionScreen(vm, onBack = { showSettings = false }) { PleosStatusSection(pleosVm) }
                } else {
                    TripScreen(tripVm, onOpenSettings = { showSettings = true })
                }
            }
        }
    }
}
