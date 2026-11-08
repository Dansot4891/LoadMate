package com.loadmate.app.core.designsystem

/** 서버 UTC ISO 시각 → 기기 현지 시각 HH:mm:ss */
fun localTime(iso: String): String = runCatching {
    java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
}.getOrDefault(iso)
