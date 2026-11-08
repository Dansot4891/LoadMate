package com.loadmate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loadmate.app.core.designsystem.PleosTheme
import com.loadmate.app.feature.settings.presentation.ConnectionScreen
import com.loadmate.app.feature.settings.presentation.ConnectionViewModel
import com.loadmate.app.feature.settings.presentation.PleosStatusSection
import com.loadmate.app.feature.settings.presentation.PleosStatusViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as LoadmateApplication).container
        setContent {
            PleosTheme {
                // 뼈대 단계: S10(설정·연결 상태) — 서버 연결 + Pleos 연동 상태. 업무 화면(S01~S09)은 이후 추가
                val vm: ConnectionViewModel = viewModel(factory = ConnectionViewModel.factory(container))
                val pleosVm: PleosStatusViewModel = viewModel(factory = PleosStatusViewModel.factory(container))
                ConnectionScreen(vm) { PleosStatusSection(pleosVm) }
            }
        }
    }
}
