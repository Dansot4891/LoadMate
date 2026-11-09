package com.loadmate.app.core.network

fun describe(e: ApiError): String = when (e) {
    ApiError.NotConfigured -> "서버 주소가 설정되지 않았습니다"
    is ApiError.Network -> "서버에 연결하지 못했습니다. 다시 시도해 주세요"
    is ApiError.Http -> e.message
}
