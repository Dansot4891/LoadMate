package com.loadmate.app.feature.records.presentation.memo

import com.loadmate.app.feature.assistant.domain.AssistantReply
import com.loadmate.app.feature.assistant.presentation.voice.interpretationLabel
import com.loadmate.app.feature.records.domain.Memo
import com.loadmate.app.feature.trip.presentation.shared.arrivalToolNotes
import com.loadmate.app.feature.trip.presentation.shared.startCheckNotes
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoRulesTest {
    private fun note(id: Int, category: String) =
        Memo(id, "vehicle", "v", "n$id", emptyList(), true, false, "screen", "shared", "t", "shared", category, null)

    private val notes = listOf(note(1, "tool"), note(2, "issue"), note(4, "other"))

    @Test fun `filter keeps only the chosen kind`() {
        assertEquals(listOf(1, 2, 4), visibleNotes(notes, NoteFilter.ALL).map { it.id })
        assertEquals(listOf(2), visibleNotes(notes, NoteFilter.ISSUE).map { it.id })
        assertEquals(listOf(4), visibleNotes(notes, NoteFilter.OTHER).map { it.id })
    }

    @Test fun `start card lists only issues and arrival card lists tools`() {
        assertEquals(listOf(2), startCheckNotes(notes).map { it.id })
        assertEquals(listOf(1), arrivalToolNotes(notes).map { it.id })
    }

    @Test fun `rule based answer is never labeled as ai`() {
        fun reply(method: String, status: String) = AssistantReply(AssistantReply.Kind.ANSWER, "s", "d", null, null, null, method, status)
        assertEquals("AI 답변", interpretationLabel(reply("ai", "ok")))
        assertEquals("AI 연결 안 됨 · 기본 답변", interpretationLabel(reply("rule_based", "unavailable")))
        assertEquals("AI 연결 안 됨 · 기본 답변", interpretationLabel(reply("rule_based", "not_configured")))
        assertEquals("기본 답변", interpretationLabel(reply("rule_based", "ok")))
    }
}
