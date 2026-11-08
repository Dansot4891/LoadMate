package com.loadmate.app.feature.fleet.domain

import com.loadmate.app.core.network.ApiResult

data class Driver(val id: String, val name: String)

/** 공용 장비 (F57·F58). status: ok | missing | broken | lent | not_returned */
data class Equipment(val id: String, val name: String, val location: String, val status: String, val note: String, val updatedBy: String?)

/** 인계 요약: 공유 기록과 미완료 업무만, 개인 기록 제외 (F61·R12). method = rule_based */
data class HandoverPreview(val lines: List<String>, val method: String, val currentDriverId: String?)

data class Handover(val id: Int, val fromDriverId: String, val toDriverId: String, val note: String, val accepted: Boolean, val lines: List<String>)

interface FleetRepository {
    suspend fun drivers(): ApiResult<List<Driver>>
    suspend fun equipment(vehicleId: String): ApiResult<List<Equipment>>
    suspend fun setEquipmentStatus(id: String, status: String, note: String?): ApiResult<Equipment>
    suspend fun handoverPreview(tripId: String): ApiResult<HandoverPreview>
    suspend fun latestHandover(tripId: String): ApiResult<Handover?>
    suspend fun createHandover(tripId: String, key: String, toDriverId: String, note: String): ApiResult<Handover>
    suspend fun acceptHandover(id: Int): ApiResult<Handover>
}
