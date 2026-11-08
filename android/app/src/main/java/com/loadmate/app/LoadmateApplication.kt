package com.loadmate.app

import android.app.Application

class LoadmateApplication : Application() {
    /** 앱 조립 지점. 구현체 연결은 여기서만 한다 (화면은 SDK·HTTP 구현을 직접 참조하지 않음). */
    val container: AppContainer by lazy { AppContainer(this) }
}
