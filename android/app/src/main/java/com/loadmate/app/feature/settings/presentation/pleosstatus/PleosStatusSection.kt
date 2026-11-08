package com.loadmate.app.feature.settings.presentation.pleosstatus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loadmate.app.feature.settings.presentation.pleosstatus.components.PleosStatusCard

@Composable
fun PleosStatusSection(vm: PleosStatusViewModel) {
    val context = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(false) }
    // 화면이 보일 때만 SDK 리스너 유지 (CLAUDE.md 원칙 8)
    LifecycleStartEffect(vm) {
        notificationsAllowed = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        vm.onAction(PleosStatusUiAction.Start)
        onStopOrDispose { vm.onAction(PleosStatusUiAction.Stop) }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    PleosStatusCard(state, notificationsAllowed)
}
