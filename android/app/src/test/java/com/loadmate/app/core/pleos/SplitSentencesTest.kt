package com.loadmate.app.core.pleos

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitSentencesTest {
    @Test
    fun `문장 끝에서 나눈다`() {
        assertEquals(listOf("A에서 박스 3개입니다.", "뒷문 쪽이에요!", "확인할까요?"), splitSentences("A에서 박스 3개입니다. 뒷문 쪽이에요! 확인할까요?"))
    }

    @Test
    fun `소수점과 마침표 없는 문장은 나누지 않는다`() {
        assertEquals(listOf("거리 3.1km 남았어요"), splitSentences("거리 3.1km 남았어요"))
    }

    @Test
    fun `빈 문장은 읽지 않는다`() {
        assertEquals(emptyList<String>(), splitSentences("   "))
    }
}
