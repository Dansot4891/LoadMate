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

sealed interface PleosStatusUiAction {
    /** 화면 표시 시작: 내비 연결 + 뒷문 구독 */
    data object Start : PleosStatusUiAction
    /** 화면 이탈: 뒷문·음성 리스너 해제 (내비는 운행 범위라 유지) */
    data object Stop : PleosStatusUiAction
    data object RequestSampleRoute : PleosStatusUiAction
    data object CancelRoute : PleosStatusUiAction
    data object Listen : PleosStatusUiAction
    data object StopListening : PleosStatusUiAction
}

class PleosStatusViewModel(
    private val navi: NaviGateway,
    private val rearDoor: RearDoorGateway,
    private val speech: SpeechGateway,
) : ViewModel() {

    val state: StateFlow<PleosStatusUiState> =
        combine(navi.state, rearDoor.state, speech.state) { n, d, sp -> PleosStatusUiState(navi = n, rearDoor = d, speech = sp) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PleosStatusUiState())

    fun onAction(action: PleosStatusUiAction) {
        when (action) {
            PleosStatusUiAction.Start -> { navi.connect(); rearDoor.start(); speech.connect() }
            // 내비는 운행 범위로 유지(원칙 8) → 여기서 해제하지 않는다. connect()는 이미 연결돼 있으면 무시
            PleosStatusUiAction.Stop -> rearDoor.stop() // 음성 연결은 음성 대화와 공유 → 해제하지 않음
            PleosStatusUiAction.RequestSampleRoute -> navi.requestRoute(SAMPLE_DESTINATION)
            PleosStatusUiAction.CancelRoute -> navi.cancelRoute()
            PleosStatusUiAction.Listen -> speech.listen()
            PleosStatusUiAction.StopListening -> speech.stopListening()
        }
    }

    override fun onCleared() {
        rearDoor.stop()
    }

    companion object {
        /** 샘플 목적지 (실측용, 실제 배송지 아님): 서울역 인근 좌표 */
        val SAMPLE_DESTINATION = NaviDestination(name = "샘플 목적지 · 서울역", latitude = 37.5547, longitude = 126.9707)

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { PleosStatusViewModel(container.naviGateway, container.rearDoorGateway, container.speechGateway) }
        }
    }
}
