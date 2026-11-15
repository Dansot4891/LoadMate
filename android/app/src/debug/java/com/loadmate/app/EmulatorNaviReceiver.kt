package com.loadmate.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.loadmate.app.core.pleos.PleosNaviGateway

/**
 * debug 빌드 전용 에뮬레이터 시연 도구. 내비 SDK 의 도착 콜백과 같은 이벤트를 넣는다 (화면·문서에 "에뮬레이터 신호"로 표시, 원칙 5).
 * 에뮬레이터 GPS 의 NMEA 검사값 오류로 내비가 실제로 도착하지 못해 둔다. release 빌드에는 포함되지 않는다.
 *
 * adb shell am broadcast --user 10 -n com.loadmate.app/.EmulatorNaviReceiver --ez destination false
 */
class EmulatorNaviReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as LoadmateApplication
        val navi = app.container.naviGateway as? PleosNaviGateway ?: return
        navi.injectArrivalForEmulator(destination = intent.getBooleanExtra("destination", false))
    }
}
