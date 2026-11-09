package com.loadmate.app.feature.records.presentation.history

import com.loadmate.app.feature.trip.domain.HistoryItem
import com.loadmate.app.shared.format.localTime
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryRulesTest {
    private val at = "2026-11-09T08:36:39Z"
    private fun item(type: String) = HistoryItem(type, "screen", null, at, type)

    /** 서버 _EVENT_LABEL 의 12종 */
    private val serverTypes = listOf(
        "baseline_confirmed", "stop_completed", "stop_added", "stops_reordered", "stop_cancelled", "cargo_qty_changed",
        "trip_ended", "pickup_added", "pickup_returned", "issue_recorded", "issue_resolved", "handover_accepted",
    )

    @Test fun `서버의 내역 종류는 빠짐없이 기능 하나에만 속한다`() {
        val groups = HistoryFilter.entries.mapNotNull { it.types }
        assertEquals(serverTypes.toSet(), groups.flatten().toSet())
        assertEquals(serverTypes.size, groups.sumOf { it.size })
    }

    @Test fun `기능을 고르면 그 종류만 보이고 모르는 종류는 전체에서만 보인다`() {
        val items = listOf(item("stop_added"), item("issue_resolved"), item("new_type"))
        assertEquals(3, visibleHistory(items, HistoryFilter.ALL).size)
        assertEquals(listOf("stop_added"), visibleHistory(items, HistoryFilter.STOP).map { it.type })
        assertEquals(listOf("issue_resolved"), visibleHistory(items, HistoryFilter.ISSUE).map { it.type })
        assertEquals(0, visibleHistory(items, HistoryFilter.TRIP).size)
    }

    @Test fun `시각과 입력 방식과 기사를 잇고 기사를 모르면 뺀다`() {
        assertEquals("${localTime(at)} · 음성 · driver-1", historyMeta(HistoryItem("t", "voice", "driver-1", at, "s")))
        assertEquals("${localTime(at)} · 화면", historyMeta(HistoryItem("t", "screen", null, at, "s")))
    }
}
