package com.loadmate.app.feature.settings.presentation.pleosstatus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.loadmate.app.AppContainer
import com.loadmate.app.core.pleos.NaviDestination
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

/** S10 Pleos 연동 상태 (F65). 실측용 샘플 목적지는 화면에 "샘플"로 표시한다. */

data class PleosStatusUiState(
    val navi: NaviState = NaviState(),
    val rearDoor: RearDoorState = RearDoorState(),
    val speech: SpeechState = SpeechState(),
    val sampleDestination: NaviDestination = PleosStatusViewModel.SAMPLE_DESTINATION,
)

