package com.loadmate.app.feature.settings.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class HealthDto(val status: String? = null, val version: String? = null, val database: String? = null)
