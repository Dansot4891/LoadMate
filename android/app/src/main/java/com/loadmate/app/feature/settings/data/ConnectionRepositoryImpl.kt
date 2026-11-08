package com.loadmate.app.feature.settings.data

import com.loadmate.app.core.network.ApiClient
import com.loadmate.app.core.network.ApiError
import com.loadmate.app.core.network.ApiResult
import com.loadmate.app.feature.settings.domain.ConnectionRepository
import com.loadmate.app.feature.settings.domain.ServerStatus

class ConnectionRepositoryImpl(private val api: ApiClient) : ConnectionRepository {
    override suspend fun checkServer(): ServerStatus = when (val result = api.get("/health", parse = HealthResponseParser::parse)) {
        is ApiResult.Success -> result.value
        is ApiResult.Failure -> when (val error = result.error) {
            ApiError.NotConfigured -> ServerStatus.NotConfigured
            is ApiError.Network -> ServerStatus.Offline(error.reason)
            is ApiError.Http -> ServerStatus.Offline("HTTP ${error.status}")
        }
    }
}
