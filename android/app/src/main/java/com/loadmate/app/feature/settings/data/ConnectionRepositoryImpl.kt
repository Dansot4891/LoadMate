package com.loadmate.app.feature.settings.data

import com.loadmate.app.feature.settings.domain.ConnectionRepository
import com.loadmate.app.feature.settings.domain.ServerStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 의존성 없는 최소 HTTP 구현. 업무 API가 늘면 공통 네트워크 모듈(OkHttp/Ktor 등)로 교체한다. */
class ConnectionRepositoryImpl(
    private val baseUrl: String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ConnectionRepository {

    override suspend fun checkServer(): ServerStatus {
        if (baseUrl.isBlank()) return ServerStatus.NotConfigured
        return withContext(io) {
            val conn = URL("${baseUrl.trimEnd('/')}/health").openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 3_000
                conn.readTimeout = 3_000
                val code = conn.responseCode
                if (code != 200) return@withContext ServerStatus.Offline("HTTP $code")
                HealthResponseParser.parse(conn.inputStream.bufferedReader().use { it.readText() })
            } catch (e: IOException) {
                ServerStatus.Offline(e.javaClass.simpleName)
            } finally {
                conn.disconnect()
            }
        }
    }
}
