package com.loadmate.app.feature.settings.data.mapper

import com.loadmate.app.feature.settings.data.dto.HealthDto
import com.loadmate.app.feature.settings.domain.DatabaseStatus
import com.loadmate.app.feature.settings.domain.ServerStatus

internal fun HealthDto.toDomain(): ServerStatus.Online = ServerStatus.Online(
    serverVersion = version ?: "?",
    database = when (database) {
        "ok" -> DatabaseStatus.OK
        "unavailable" -> DatabaseStatus.UNAVAILABLE
        else -> DatabaseStatus.UNKNOWN
    },
)
