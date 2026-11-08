package com.loadmate.app.feature.trip.data

import com.loadmate.app.core.network.ApiResult
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.domain.TripRepository
import java.time.Instant

/**
 * 이번 실행 중 마지막으로 받은 운행을 메모리에 보관한다 (R11·F66: 통신 실패 시 최근 목록 유지).
 * 캐시는 서버에서 받은 실제 값이며, 화면은 "오프라인 · 마지막 수신 시각"으로 구분해 표시한다 (원칙 5).
 * 상태 변경은 캐시로 처리하지 않는다 → 재연결 후 같은 요청 ID 로 다시 보낸다.
 */
class CachingTripRepository(private val inner: TripRepository) : TripRepository by inner {
    private val memory = mutableMapOf<String, Pair<Trip, Instant>>()

    override suspend fun getTrip(tripId: String): ApiResult<Trip> {
        val r = inner.getTrip(tripId)
        if (r is ApiResult.Success) {
            memory[tripId] = r.value to Instant.now()
        }
        return r
    }

    /** 이번 실행 중 마지막으로 받은 운행과 수신 시각 */
    fun cached(tripId: String): Pair<Trip, Instant>? = memory[tripId]
}
