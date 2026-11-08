package com.loadmate.app.feature.trip.presentation.select

import com.loadmate.app.core.network.ApiError
import com.loadmate.app.feature.trip.domain.TripStatus
import com.loadmate.app.feature.trip.presentation.select.components.statusText
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectRulesTest {
    @Test fun `서버 미설정과 연결 실패를 구분한다`() {
        assertEquals("서버 미설정", errorText(ApiError.NotConfigured))
        assertEquals("서버에 연결할 수 없습니다", errorText(ApiError.Network("offline")))
    }

    @Test fun `운행 상태를 기사에게 표시한다`() {
        assertEquals("예정", statusText(TripStatus.PLANNED))
        assertEquals("미완료 종료", statusText(TripStatus.ENDED_INCOMPLETE))
    }
}
