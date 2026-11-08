package com.loadmate.app.core.session

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 현재 기사 선택 (F56). 인증이 아니라 사용자 선택이며 기기에만 저장한다. */
class DriverStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val _driverId = MutableStateFlow(prefs.getString(KEY, null))
    val driverId: StateFlow<String?> = _driverId.asStateFlow()

    private val _driverName = MutableStateFlow(prefs.getString(KEY_NAME, null))
    val driverName: StateFlow<String?> = _driverName.asStateFlow()

    fun select(id: String, name: String) {
        prefs.edit().putString(KEY, id).putString(KEY_NAME, name).apply()
        _driverId.value = id
        _driverName.value = name
    }

    private companion object {
        const val KEY = "driver_id"
        const val KEY_NAME = "driver_name"
    }
}
