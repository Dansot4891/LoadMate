package com.loadmate.app.feature.trip.data

import com.loadmate.app.core.network.ApiClient
import com.loadmate.app.feature.trip.domain.ComparisonState
import com.loadmate.app.feature.trip.domain.ObservationSource
import com.loadmate.app.feature.trip.domain.StopStatus
import com.loadmate.app.feature.trip.domain.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 픽스처는 실제 서버 응답을 저장한 것 (2026-11-08, backend features/trip) */
class TripJsonParserTest {
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream("trip/$name")!!.bufferedReader().readText()

    @Test
    fun `운행 목록은 서버 출처와 건수를 유지한다`() {
        val trips = TripJsonParser.tripList("""{"trips":[{"id":"sample-trip-1","name":"샘플 운행","status":"planned","data_source":"sample","stop_count":3}]}""")
        assertEquals(1, trips.size)
        assertEquals("sample-trip-1", trips.single().id)
        assertEquals("sample", trips.single().dataSource)
        assertEquals(3, trips.single().stopCount)
    }

    @Test
    fun `운행 응답 파싱`() {
        val t = TripJsonParser.trip(res("trip_in_progress.json"))
        assertEquals("sample", t.dataSource)
        assertEquals(TripStatus.IN_PROGRESS, t.status)
        assertEquals(mapOf("box" to 10), t.baseline!!.counts)
        assertNull(t.baseline!!.observationId)
        assertEquals(listOf("A", "B", "C"), t.stops.map { it.label })
        assertEquals(mapOf("box" to 10), t.plannedTotals)
        assertEquals(mapOf("box" to "박스"), t.kindNames)
        assertEquals("stop-a", t.currentStop!!.id)
        assertEquals(ComparisonState.MISMATCH, t.comparison.state)
        assertEquals(ObservationSource.SAMPLE, t.comparison.observation!!.source)
    }

    @Test
    fun `확인 필요 응답 파싱 - 저장되지 않음`() {
        val r = TripJsonParser.completeResult(res("complete_needs_confirmation.json"))
        assertFalse(r.completed)
        assertEquals(StopStatus.PENDING, r.stop.status)
        assertEquals(mapOf("box" to 10), r.expectedRemaining)
        assertEquals(ComparisonState.MISMATCH, r.comparison.state)
        assertEquals(7, r.comparison.differences.single().expected)
        assertEquals(8, r.comparison.differences.single().observed)
        assertTrue(r.comparison.countOnly)
    }

    @Test
    fun `오류 응답 코드 추출`() {
        assertEquals("stop_already_completed" to "이미 완료된 방문지입니다",
            ApiClient.errorOf("""{"detail":{"code":"stop_already_completed","message":"이미 완료된 방문지입니다"}}"""))
        assertEquals(null to "입력값을 확인하세요", ApiClient.errorOf("""{"detail":[{"msg":"bad"}]}"""))
        assertEquals(null to null, ApiClient.errorOf("<html>"))
    }
}
