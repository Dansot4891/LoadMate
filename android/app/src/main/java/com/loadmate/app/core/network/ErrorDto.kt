package com.loadmate.app.core.network

import kotlinx.serialization.Serializable

@Serializable internal data class ErrorEnvelopeDto(val detail: ErrorDetailDto? = null)
@Serializable internal data class ErrorDetailDto(val code: String? = null, val message: String? = null)
