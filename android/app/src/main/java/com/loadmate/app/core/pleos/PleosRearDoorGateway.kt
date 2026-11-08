package com.loadmate.app.core.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Vehicle 2.3.2 Door Position(REAR) 구독. 권한 `CAR_DOORS`.
 * 참고 구현: PleosLab vehicle/real/RealVehicleDataSource.kt (typed Door API 경로).
 */
class PleosRearDoorGateway(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : RearDoorGateway {

    private val appContext = context.applicationContext
    private var vehicle: Vehicle? = null
    private val rear = DoorArea(DoorValue.REAR)

    private val _state = MutableStateFlow(RearDoorState())
    override val state: StateFlow<RearDoorState> = _state.asStateFlow()

    private val listener = object : DoorPositionListener {
        override fun onPositionUpdated(pos: Int?) {
            Log.i(TAG, "REAR position=$pos")
            _state.update { it.copy(position = pos, updatedAtMillis = clock(), updateCount = it.updateCount + 1) }
        }

        override fun onFailed(e: Exception) = fail(e)
    }

    override fun start() {
        if (vehicle != null) return
        try {
            val v = Vehicle(appContext)
            v.initialize()
            vehicle = v
            val door = v.getDoor()
            door.checkDoorPositionCapabilities(
                onComplete = { bounds: Map<DoorArea, ai.pleos.playground.vehicle.api.model.capability.AreaBounds<Int>> ->
                    val b = bounds.entries.firstOrNull { DoorValue.REAR in it.key.values }?.value
                    _state.update { it.copy(minPosition = b?.minValue, maxPosition = b?.maxValue) }
                },
                onFailed = { e -> Log.w(TAG, "REAR capability 조회 실패: $e") },
            )
            door.registerDoorPosition(rear, listener)
            _state.update { it.copy(connection = RearDoorConnection.Subscribed, updateCount = 0) }
        } catch (t: Throwable) {
            fail(t)
            releaseVehicle()
        }
    }

    override fun stop() {
        val v = vehicle ?: return
        runCatching { v.getDoor().unregisterDoorPosition(rear, listener) }
        releaseVehicle()
        _state.update { it.copy(connection = RearDoorConnection.Stopped) }
    }

    private fun releaseVehicle() {
        runCatching { vehicle?.release() }
        vehicle = null
    }

    private fun fail(t: Throwable) {
        Log.w(TAG, "REAR 실패: $t")
        _state.update { it.copy(connection = RearDoorConnection.Failed(t.toString())) }
    }

    private companion object {
        const val TAG = "LoadmateRearDoor"
    }
}
