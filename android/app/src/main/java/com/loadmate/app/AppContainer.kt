package com.loadmate.app

import android.content.Context
import com.loadmate.app.core.pleos.NaviGateway
import com.loadmate.app.core.pleos.PleosNaviGateway
import com.loadmate.app.core.pleos.PleosRearDoorGateway
import com.loadmate.app.core.pleos.RearDoorGateway
import com.loadmate.app.core.pleos.PleosSpeechGateway
import com.loadmate.app.core.pleos.SpeechGateway
import com.loadmate.app.feature.settings.data.HttpConnectionRepository
import com.loadmate.app.feature.settings.domain.ConnectionRepository
import com.loadmate.app.feature.trip.data.HttpTripRepository
import com.loadmate.app.feature.trip.domain.TripRepository

/** 수동 의존성 주입 컨테이너. Feature가 늘어 관리가 어려워지면 Hilt 도입을 검토한다. */
class AppContainer(appContext: Context) {
    val apiBaseUrl: String = BuildConfig.API_BASE_URL

    val connectionRepository: ConnectionRepository by lazy { HttpConnectionRepository(apiBaseUrl) }
    val tripRepository: TripRepository by lazy { HttpTripRepository(apiBaseUrl) }

    /** 시연 운행 (서버 샘플 시드 backend/app/features/trip/seed.py). 운행 목록 화면이 생기면 선택으로 바꾼다 */
    val sampleTripId = "sample-trip-1"

    // Pleos SDK 구현은 여기서만 연결한다. 화면은 인터페이스만 받는다.
    val naviGateway: NaviGateway by lazy { PleosNaviGateway(appContext) }
    val rearDoorGateway: RearDoorGateway by lazy { PleosRearDoorGateway(appContext) }
    val speechGateway: SpeechGateway by lazy { PleosSpeechGateway(appContext) }
}
