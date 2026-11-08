package com.loadmate.app.feature.trip.presentation.sitework

import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.core.network.*
import com.loadmate.app.core.notify.DriverNotifier
import com.loadmate.app.feature.trip.domain.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

internal fun TripCoordinator.complete(driverConfirmed: Boolean) {
    val s = local.value
    val trip = s.trip ?: return
    val stop = trip.currentStop ?: return
    if (s.busy) return
    val op = "complete:${stop.id}"
    val key = keyFor(op)
    local.update { it.copy(busy = true) }
    scope.launch {
        when (val r = repo.completeStop(trip.id, stop.id, key, s.deliveredDraft, driverConfirmed)) {
            is ApiResult.Success -> {
                val res = r.value
                if (res.completed) {
                    done(op)
                    val how = if (res.stop.resolution == "driver_confirmed") "기사 확인으로 완료" else "관측과 일치해 완료"
                    local.update {
                        it.copy(busy = false, pendingConfirmation = null, step = TripStep.DRIVING,
                            message = UiMessage("${res.stop.label} 하차 완료 · $how · 예상 잔량 ${countsText(res.expectedRemaining, trip)}", false))
                    }
                    reloadQuietly()
                } else {
                    // 저장되지 않음. 같은 키로 다시 보낼 수 있다. 짧은 음성으로 의심 안내 (F25·R09)
                    local.update { it.copy(busy = false, step = TripStep.MISMATCH, pendingConfirmation = res.comparison) }
                    speech?.speak(mismatchSpeech(res.comparison, trip, stop.label))
                }
            }
            is ApiResult.Failure -> {
                if ((r.error as? ApiError.Http)?.code == "stop_already_completed") {
                    done(op)
                    local.update { it.copy(step = TripStep.DRIVING) }
                    reloadQuietly()
                }
                fail(r.error)
            }
        }
    }
}

internal fun TripCoordinator.submitPickup() {
    val d = local.value.pickupDraft ?: return
    val trip = local.value.trip ?: return
    val stopId = trip.currentStop?.id
    val fromMismatch = local.value.step == TripStep.MISMATCH
    val text = "회수 기록 · ${d.itemName} ${d.qty}개 → ${d.returnPlace}" + if (fromMismatch) " · 예상 잔량에 반영됐습니다. 다시 완료 요청하세요" else ""
    change("pickup:$stopId:${d.itemName}:${d.qty}:${d.returnPlace}", text, after = {
        // S06 에서 "회수품이에요" 로 왔으면 현장 작업으로 돌아가 같은 요청 ID 로 다시 완료 요청
        if (fromMismatch) local.update { it.copy(step = TripStep.SITE_WORK, pendingConfirmation = null) }
    }) { t, k ->
        repo.addPickup(t.id, k, stopId, d.itemName, d.kind, d.qty, d.returnPlace, "screen")
    }
    local.update { it.copy(pickupDraft = null) }
}
