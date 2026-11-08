package com.loadmate.app.core.network

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

sealed interface ApiError {
    /** 서버에 닿지 못함. 같은 요청 ID 로 다시 보내면 중복 반영 없이 재시도된다 */
    data class Network(val reason: String) : ApiError
    /** 서버 오류 응답 {"detail":{"code","message"}} */
    data class Http(val status: Int, val code: String?, val message: String) : ApiError
    data object NotConfigured : ApiError
}

