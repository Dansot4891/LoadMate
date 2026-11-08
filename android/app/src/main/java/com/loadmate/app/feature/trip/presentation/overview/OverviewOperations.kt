package com.loadmate.app.feature.trip.presentation.overview

import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.core.network.*
import com.loadmate.app.core.notify.DriverNotifier
import com.loadmate.app.feature.trip.domain.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

internal fun TripCoordinator.moveStop(stopId: String, up: Boolean) {
    val trip = local.value.trip ?: return
    val ids = trip.pendingStops.map { it.id }.toMutableList()
    val i = ids.indexOf(stopId)
    val j = if (up) i - 1 else i + 1
    if (i < 0 || j !in ids.indices) return
    ids[i] = ids[j].also { ids[j] = ids[i] }
    change("reorder:${ids.joinToString(",")}", "방문 순서 변경 · 내비에 다시 보내야 반영됩니다") { t, k -> repo.reorderStops(t.id, k, ids) }
}

internal fun TripCoordinator.recordSampleIssue(kind: String) {
    val trip = local.value.trip ?: return
    val zone = trip.currentStop?.cargo?.firstOrNull()?.zone
    val label = issueLabel(kind)
    val at = now().truncatedTo(ChronoUnit.SECONDS).toString()
    change("issue:$kind:$at", "$label 기록 (샘플 관측)") { t, k -> repo.recordIssue(t.id, k, kind, zone, "샘플 관측", ObservationSource.SAMPLE, at) }
    // 주행 중에는 화면을 바꾸지 않고 짧게 알린다 (R09·F32)
    speech?.speak("$label. 안전한 곳에 정차한 뒤 확인하세요.")
    notify(DriverNotifier.ID_ISSUE, label, "정차 후 Loadmate 에서 확인하세요 (샘플 관측)")
}
