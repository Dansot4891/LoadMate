package com.loadmate.app.feature.settings.presentation.pleosstatus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.loadmate.app.AppContainer
import com.loadmate.app.core.pleos.NaviGateway
import com.loadmate.app.core.pleos.NaviState
import com.loadmate.app.core.pleos.RearDoorGateway
import com.loadmate.app.core.pleos.RearDoorState
import com.loadmate.app.core.pleos.SpeechGateway
import com.loadmate.app.core.pleos.SpeechState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** S10 Pleos 연동 상태 (F65). */

sealed interface PleosStatusUiAction {
    /** 화면 표시 시작: 내비 연결 + 뒷문 구독 */
    data object Start : PleosStatusUiAction
    /** 화면 이탈: 뒷문·음성 리스너 해제 (내비는 운행 범위라 유지) */
    data object Stop : PleosStatusUiAction
}

