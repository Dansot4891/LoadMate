package com.loadmate.app.core.network

import com.loadmate.app.core.network.ApiError
import com.loadmate.app.core.network.ApiResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Loadmate 서버 공통 HTTP (HttpURLConnection, 의존성 없음).
 * - 현재 기사를 `X-Driver-Id` 로 보낸다 (인증 아님, 사용자 선택 · F56)
 * - 오류 형식 {"detail":{"code","message"}} 를 [ApiError.Http] 로 바꾼다
 * - 실패 시 데모 데이터로 바꾸지 않는다 (원칙 5)
 */
class ApiClient(
    private val baseUrl: String,
    private val driverId: () -> String?,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun <T> get(path: String, query: Map<String, String?> = emptyMap(), parse: (String) -> T): ApiResult<T> =
        call("GET", path + queryString(query), null, parse)

    suspend fun <T> post(path: String, body: String, parse: (String) -> T): ApiResult<T> = call("POST", path, body, parse)

    suspend fun <T> put(path: String, body: String, parse: (String) -> T): ApiResult<T> = call("PUT", path, body, parse)

    private fun queryString(q: Map<String, String?>): String {
        val parts = q.filterValues { !it.isNullOrBlank() }.map { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }
        return if (parts.isEmpty()) "" else "?" + parts.joinToString("&")
    }

    private suspend fun <T> call(method: String, path: String, body: String?, parse: (String) -> T): ApiResult<T> {
        if (baseUrl.isBlank()) return ApiResult.Failure(ApiError.NotConfigured)
        return withContext(io) {
            val conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 3_000
                conn.readTimeout = 5_000
                conn.requestMethod = method
                conn.setRequestProperty("Accept", "application/json")
                driverId()?.let { conn.setRequestProperty("X-Driver-Id", it) }
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code in 200..299) ApiResult.Success(parse(text))
                else {
                    val (errCode, msg) = errorOf(text)
                    ApiResult.Failure(ApiError.Http(code, errCode, msg ?: "HTTP $code"))
                }
            } catch (e: IOException) {
                ApiResult.Failure(ApiError.Network(e.javaClass.simpleName))
            } catch (e: SerializationException) {
                ApiResult.Failure(ApiError.Http(-1, "bad_response", "응답 형식 오류"))
            } finally {
                conn.disconnect()
            }
        }
    }

    companion object {
        /** {"detail":{"code","message"}} → (code, message). FastAPI 422 는 detail 이 배열이라 code 없음 */
        private val json = Json { ignoreUnknownKeys = true }
        fun errorOf(body: String): Pair<String?, String?> = runCatching {
            val root = json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject ?: return@runCatching null to null
            val detail = root["detail"]
            if (detail is kotlinx.serialization.json.JsonArray) return@runCatching null to "입력값을 확인하세요"
            val error = json.decodeFromString<ErrorEnvelopeDto>(body).detail
            error?.code to error?.message
        }.getOrDefault(null to null)
    }
}
