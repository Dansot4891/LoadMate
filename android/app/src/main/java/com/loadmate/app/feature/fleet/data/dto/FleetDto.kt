package com.loadmate.app.feature.fleet.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class DriversDto(val drivers: List<DriverDto>)
@Serializable data class DriverDto(val id: String, val name: String)
@Serializable data class EquipmentListDto(val equipment: List<EquipmentDto>)
@Serializable data class EquipmentDto(val id: String, val name: String, val location: String, val status: String, val note: String = "", @SerialName("updated_by") val updatedBy: String? = null)
@Serializable data class HandoverPreviewDto(@SerialName("summary_lines") val summaryLines: List<String>, @SerialName("summary_method") val summaryMethod: String, @SerialName("current_driver_id") val currentDriverId: String? = null)
@Serializable data class HandoverEnvelopeDto(val handover: HandoverDto?)
@Serializable data class HandoverDto(val id: Int, @SerialName("from_driver_id") val fromDriverId: String, @SerialName("to_driver_id") val toDriverId: String, val note: String = "", val status: String, val summary: HandoverSummaryDto)
@Serializable data class HandoverSummaryDto(@SerialName("summary_lines") val summaryLines: List<String>)
