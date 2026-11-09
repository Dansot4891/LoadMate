package com.loadmate.app.feature.records.presentation.history

import com.loadmate.app.feature.trip.domain.HistoryItem
import com.loadmate.app.shared.format.localTime

/** 내역을 기능별로 거르는 기준. types 가 null 이면 전부. 서버가 새 종류를 보내면 "전체"에서만 보인다 */
enum class HistoryFilter(val label: String, val types: Set<String>?) {
    ALL("전체", null),
    STOP("방문지", setOf("stop_added", "stops_reordered", "stop_cancelled", "stop_completed", "cargo_qty_changed")),
    CARGO("적재·회수", setOf("baseline_confirmed", "pickup_added", "pickup_returned")),
    ISSUE("이상", setOf("issue_recorded", "issue_resolved")),
    TRIP("운행", setOf("trip_ended", "handover_accepted")),
}

data class HistoryUiState(
    val items: List<HistoryItem> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
    val message: String? = null,
) {
    val shown: List<HistoryItem> get() = visibleHistory(items, filter)
}

internal fun visibleHistory(items: List<HistoryItem>, filter: HistoryFilter): List<HistoryItem> =
    filter.types?.let { types -> items.filter { it.type in types } } ?: items

/** 내역 한 줄의 부가 정보: 시각 · 입력 방식 · 기사 (기사를 모르면 뺀다) */
internal fun historyMeta(h: HistoryItem): String =
    listOfNotNull(localTime(h.createdAt), if (h.source == "voice") "음성" else "화면", h.actorId).joinToString(" · ")
