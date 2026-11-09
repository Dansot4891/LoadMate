package com.loadmate.app.feature.trip.presentation.overview

import com.loadmate.app.feature.trip.presentation.driving.askRoute
import com.loadmate.app.feature.trip.presentation.shared.*
import com.loadmate.app.core.network.*
import com.loadmate.app.feature.trip.domain.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** 출발 확인: 기사가 "맞아요"를 누르면 그 수량을 최초 기준으로 확정하고 바로 내비 전달 확인으로 넘어간다 */
internal fun TripCoordinator.confirmDeparture() {
    val s = local.value
    val trip = s.trip ?: return
    if (s.busy) return
    val load = departureLoad(trip)
    val op = "baseline:${load.counts}"
    val key = keyFor(op)
    local.update { it.copy(busy = true, departureDialog = false) }
    scope.launch {
        when (val r = repo.confirmBaseline(trip.id, key, load.counts, load.observationId)) {
            is ApiResult.Success -> {
                done(op)
                local.update { it.copy(busy = false) }
                applyTrip(r.value)
                askRoute(r.value, announceGap = false)
            }
            is ApiResult.Failure -> {
                if ((r.error as? ApiError.Http)?.code == "baseline_already_confirmed") done(op)
                fail(r.error)
                reloadQuietly()
            }
        }
    }
}

/** 입력한 주소의 위치 확인. 실패하면 이유를 입력창에 보여 준다 */
internal fun TripCoordinator.checkAddress(address: String) {
    if (local.value.addressCheck.checking) return
    local.update { it.copy(addressCheck = AddressCheckState(checking = true)) }
    scope.launch {
        val next = when (val r = repo.checkAddress(address)) {
            is ApiResult.Success -> if (r.value) AddressCheckState(foundAddress = address) else AddressCheckState(notFoundAddress = address)
            is ApiResult.Failure -> AddressCheckState(error = describe(r.error))
        }
        local.update { it.copy(addressCheck = next) }
    }
}
