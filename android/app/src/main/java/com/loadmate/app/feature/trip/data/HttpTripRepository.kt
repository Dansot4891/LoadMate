package com.loadmate.app.feature.trip.data

import com.loadmate.app.feature.trip.domain.ApiError
import com.loadmate.app.feature.trip.domain.ApiResult
import com.loadmate.app.feature.trip.domain.Comparison
import com.loadmate.app.feature.trip.domain.CompleteResult
import com.loadmate.app.feature.trip.domain.Counts
import com.loadmate.app.feature.trip.domain.ObservationSource
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.domain.TripRepository
import com.loadmate.app.feature.trip.domain.TripSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 운행 API HTTP 구현 (HttpURLConnection, 의존성 없음). 실패 시 데모 데이터로 바꾸지 않고 오류를 그대로 돌려준다 (원칙 5). */
class HttpTripRepository(
    private val baseUrl: String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : TripRepository {

    override suspend fun listTrips(): ApiResult<List<TripSummary>> = call("GET", "/trips", null, TripJsonParser::tripList)

    override suspend fun getTrip(tripId: String): ApiResult<Trip> = call("GET", "/trips/$tripId", null, TripJsonParser::trip)

    override suspend fun recordObservation(
        tripId: String, idempotencyKey: String, source: ObservationSource, counts: Counts?, observedAtIso: String,
    ): ApiResult<Comparison> {
        val body = JSONObject()
            .put("idempotency_key", idempotencyKey)
            .put("source", source.wire)
            .put("status", if (counts == null) "unavailable" else "ok")
            .put("counts", counts?.let(TripJsonParser::countsJson) ?: JSONObject.NULL)
            .put("observed_at", observedAtIso)
        return call("POST", "/trips/$tripId/observations", body, TripJsonParser::observationComparison)
    }

    override suspend fun confirmBaseline(tripId: String, idempotencyKey: String, counts: Counts, observationId: Int?): ApiResult<Trip> {
        val body = JSONObject()
            .put("idempotency_key", idempotencyKey)
            .put("counts", TripJsonParser.countsJson(counts))
            .put("observation_id", observationId ?: JSONObject.NULL)
        return call("POST", "/trips/$tripId/baseline", body, TripJsonParser::baselineTrip)
    }

    override suspend fun completeStop(
        tripId: String, stopId: String, idempotencyKey: String, delivered: Counts, driverConfirmed: Boolean,
    ): ApiResult<CompleteResult> {
        val body = JSONObject()
            .put("idempotency_key", idempotencyKey)
            .put("delivered", TripJsonParser.countsJson(delivered))
            .put("resolution", if (driverConfirmed) "driver_confirmed" else JSONObject.NULL)
        return call("POST", "/trips/$tripId/stops/$stopId/complete", body, TripJsonParser::completeResult)
    }

    private suspend fun <T> call(method: String, path: String, body: JSONObject?, parse: (String) -> T): ApiResult<T> {
        if (baseUrl.isBlank()) return ApiResult.Failure(ApiError.NotConfigured)
        return withContext(io) {
            val conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 3_000
                conn.readTimeout = 5_000
                conn.requestMethod = method
                conn.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code in 200..299) {
                    ApiResult.Success(parse(text))
                } else {
                    val (errCode, msg) = TripJsonParser.error(text)
                    ApiResult.Failure(ApiError.Http(code, errCode, msg ?: "HTTP $code"))
                }
            } catch (e: IOException) {
                ApiResult.Failure(ApiError.Network(e.javaClass.simpleName))
            } catch (e: JSONException) {
                ApiResult.Failure(ApiError.Http(-1, "bad_response", "응답 형식 오류"))
            } finally {
                conn.disconnect()
            }
        }
    }
}
