package com.loadmate.app.feature.fleet.data.mapper

import com.loadmate.app.feature.fleet.data.dto.*
import com.loadmate.app.feature.fleet.domain.*

internal fun DriverDto.toDomain() = Driver(id, name)
internal fun EquipmentDto.toDomain() = Equipment(id, name, location, status, note, updatedBy)
internal fun HandoverPreviewDto.toDomain() = HandoverPreview(summaryLines, summaryMethod, currentDriverId)
internal fun HandoverDto.toDomain() = Handover(id, fromDriverId, toDriverId, note, status == "accepted", summary.summaryLines)
