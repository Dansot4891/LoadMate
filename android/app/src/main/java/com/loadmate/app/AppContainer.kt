package com.loadmate.app

import android.content.Context
import com.loadmate.app.core.network.ApiClient
import com.loadmate.app.core.notify.DriverNotifier
import com.loadmate.app.core.pleos.NaviGateway
import com.loadmate.app.core.pleos.PleosNaviGateway
import com.loadmate.app.core.pleos.PleosRearDoorGateway
import com.loadmate.app.core.pleos.PleosSpeechGateway
import com.loadmate.app.core.pleos.PleosSpeechOutput
import com.loadmate.app.core.pleos.RearDoorGateway
import com.loadmate.app.core.pleos.SpeechGateway
import com.loadmate.app.core.pleos.SpeechOutput
import com.loadmate.app.core.session.DriverStore
import com.loadmate.app.feature.assistant.data.AssistantRepositoryImpl
import com.loadmate.app.feature.assistant.domain.AssistantRepository
import com.loadmate.app.feature.fleet.data.FleetRepositoryImpl
import com.loadmate.app.feature.fleet.domain.FleetRepository
import com.loadmate.app.feature.records.data.MemoRepositoryImpl
import com.loadmate.app.feature.records.domain.MemoRepository
import com.loadmate.app.feature.settings.data.ConnectionRepositoryImpl
import com.loadmate.app.feature.settings.domain.ConnectionRepository
import com.loadmate.app.feature.trip.data.CachingTripRepository
import com.loadmate.app.feature.trip.data.TripRepositoryImpl
import com.loadmate.app.feature.trip.data.TripSession

/** 수동 의존성 주입 컨테이너. 구현체 연결은 여기서만 한다. */
class AppContainer(appContext: Context) {
    val apiBaseUrl: String = BuildConfig.API_BASE_URL

    val driverStore = DriverStore(appContext)
    private val api = ApiClient(apiBaseUrl, driverId = { driverStore.driverId.value })

    val connectionRepository: ConnectionRepository by lazy { ConnectionRepositoryImpl(apiBaseUrl) }
    val tripRepository: CachingTripRepository by lazy { CachingTripRepository(TripRepositoryImpl(api)) }
    val memoRepository: MemoRepository by lazy { MemoRepositoryImpl(api) }
    val fleetRepository: FleetRepository by lazy { FleetRepositoryImpl(api) }
    val assistantRepository: AssistantRepository by lazy { AssistantRepositoryImpl(api) }

    /** 시연 운행 (서버 샘플 시드). 운행 목록에서 고르는 화면이 생기면 선택으로 바꾼다 */
    val sampleTripId = "sample-trip-1"

    // Pleos SDK 구현은 여기서만 연결한다. 화면은 인터페이스만 받는다.
    val naviGateway: NaviGateway by lazy { PleosNaviGateway(appContext) }
    val tripSession: TripSession by lazy { TripSession(tripRepository, naviGateway, speechOutput) { id, title, message -> notifier.notify(id, title, message) } }
    val rearDoorGateway: RearDoorGateway by lazy { PleosRearDoorGateway(appContext) }
    val speechGateway: SpeechGateway by lazy { PleosSpeechGateway(appContext) }
    val speechOutput: SpeechOutput by lazy { PleosSpeechOutput(appContext) }
    val notifier: DriverNotifier by lazy { DriverNotifier(appContext) }

}
