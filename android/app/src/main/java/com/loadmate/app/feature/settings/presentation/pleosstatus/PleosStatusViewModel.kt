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
import com.loadmate.app.core.pleos.SpeechOutput
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** S10 Pleos 연동 상태 (F65). */
class PleosStatusViewModel(
    private val navi: NaviGateway,
    private val rearDoor: RearDoorGateway,
    private val speech: SpeechGateway,
    speechOutput: SpeechOutput,
) : ViewModel() {

    val state: StateFlow<PleosStatusUiState> =
        combine(navi.state, rearDoor.state, speech.state, speechOutput.available) { n, d, sp, tts ->
            PleosStatusUiState(navi = n, rearDoor = d, speech = sp, ttsAvailable = tts)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PleosStatusUiState())

    fun onAction(action: PleosStatusUiAction) {
        when (action) {
            PleosStatusUiAction.Start -> { navi.connect(); rearDoor.start(); speech.connect() }
            // 내비는 운행 범위로 유지(원칙 8) → 여기서 해제하지 않는다. connect()는 이미 연결돼 있으면 무시
            PleosStatusUiAction.Stop -> rearDoor.stop() // 음성 연결은 음성 대화와 공유 → 해제하지 않음
        }
    }

    override fun onCleared() {
        rearDoor.stop()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { PleosStatusViewModel(container.naviGateway, container.rearDoorGateway, container.speechGateway, container.speechOutput) }
        }
    }
}
