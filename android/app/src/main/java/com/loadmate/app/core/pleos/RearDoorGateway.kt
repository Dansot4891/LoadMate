package com.loadmate.app.core.pleos

import kotlinx.coroutines.flow.StateFlow

/**
 * 뒷문(REAR, 트렁크·테일게이트) 위치 구독 경계. 하역 시작·재출발 확인 트리거 후보 (planning-review §3 추가 제안).
 * 이 신호는 업무 이벤트의 "근거"일 뿐, 배송 완료를 확정하지 않는다 (CLAUDE.md 원칙 2).
 * 구현: [PleosRearDoorGateway] (Vehicle 2.3.2 Door).
 */
interface RearDoorGateway {
    val state: StateFlow<RearDoorState>

    /** 서비스 연결 + 범위 조회 + 구독. 이미 구독 중이면 무시. */
    fun start()

    /** 구독 해제 후 Vehicle release. 화면 이탈 시 호출. */
    fun stop()

    /** 운행 중 화면과 무관하게 구독을 유지한다. 유지 중에는 화면 쪽 [stop] 이 구독을 끊지 않는다. */
    fun holdForTrip() = start()

    fun releaseTripHold() = stop()
}

sealed interface RearDoorConnection {
    data object Stopped : RearDoorConnection
    data object Subscribed : RearDoorConnection
    data class Failed(val reason: String) : RearDoorConnection
}

data class RearDoorState(
    val connection: RearDoorConnection = RearDoorConnection.Stopped,
    /** SDK 원시 위치값. 0 = 닫힘. 범위는 [minPosition]..[maxPosition] (에뮬레이터 REAR 0..100) */
    val position: Int? = null,
    val minPosition: Int? = null,
    val maxPosition: Int? = null,
    val updatedAtMillis: Long? = null,
    /** 구독 이후 받은 알림 수 (초기 알림 포함) */
    val updateCount: Int = 0,
) {
    val isOpen: Boolean? get() = position?.let { it > (minPosition ?: 0) }
}
