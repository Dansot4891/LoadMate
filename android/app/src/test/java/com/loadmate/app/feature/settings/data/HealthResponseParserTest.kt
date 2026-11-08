package com.loadmate.app.feature.settings.data

import com.loadmate.app.feature.settings.domain.DatabaseStatus
import com.loadmate.app.feature.settings.domain.ServerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthResponseParserTest {
    @Test
    fun `db ok 응답은 Online OK`() {
        val s = HealthResponseParser.parse("""{"status":"ok","version":"0.1.0","database":"ok"}""")
        assertEquals(ServerStatus.Online("0.1.0", DatabaseStatus.OK), s)
    }

    @Test
    fun `db unavailable 응답은 Online UNAVAILABLE`() {
        val s = HealthResponseParser.parse("""{"status":"degraded","version":"0.1.0","database":"unavailable"}""")
        assertEquals(ServerStatus.Online("0.1.0", DatabaseStatus.UNAVAILABLE), s)
    }

    @Test
    fun `JSON이 아니면 Offline`() {
        assertTrue(HealthResponseParser.parse("<html>") is ServerStatus.Offline)
    }
}
