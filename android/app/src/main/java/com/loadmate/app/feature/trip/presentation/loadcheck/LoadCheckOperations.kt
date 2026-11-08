package com.loadmate.app.feature.trip.presentation.loadcheck

import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.core.network.*
import com.loadmate.app.core.notify.DriverNotifier
import com.loadmate.app.feature.trip.domain.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

internal fun TripCoordinator.recordObservation(unavailable: Boolean) {
    val s = local.value
    val trip = s.trip ?: return
    if (s.busy) return
    val key = keyFor("observation")
    local.update { it.copy(busy = true) }
    scope.launch {
        val counts = if (unavailable) null else s.observationDraft
        val at = now().truncatedTo(ChronoUnit.SECONDS).toString()
        val zones = if (unavailable || s.observationSource != ObservationSource.SAMPLE) null else {
            val delivered = if (s.step == TripStep.SITE_WORK) s.deliveredDraft else emptyMap()
            sampleZones(trip, s.observationDraft, trip.currentStop?.id.takeIf { s.step == TripStep.SITE_WORK }, delivered)
        }
        when (val r = repo.recordObservation(trip.id, key, s.observationSource, counts, at, zones)) {
            is ApiResult.Success -> {
                done("observation")
                local.update { it.copy(busy = false, lastComparison = r.value, message = UiMessage("관측 기록됨 · ${s.observationSource.label}", false)) }
                reloadQuietly()
            }
            is ApiResult.Failure -> fail(r.error)
        }
    }
}

internal fun TripCoordinator.confirmBaseline() {
    val s = local.value
    val trip = s.trip ?: return
    if (s.busy) return
    val key = keyFor("baseline")
    val obsId = s.lastComparison?.observation?.id
    local.update { it.copy(busy = true) }
    scope.launch {
        when (val r = repo.confirmBaseline(trip.id, key, s.observationDraft, obsId)) {
            is ApiResult.Success -> {
                done("baseline")
                local.update { it.copy(busy = false, message = UiMessage("최초 적재 기준 확정 · ${countsText(r.value.baseline?.counts, r.value)}", false)) }
                applyTrip(r.value, keepStep = false)
            }
            is ApiResult.Failure -> {
                if ((r.error as? ApiError.Http)?.code == "baseline_already_confirmed") done("baseline")
                fail(r.error)
                reloadQuietly()
            }
        }
    }
}
