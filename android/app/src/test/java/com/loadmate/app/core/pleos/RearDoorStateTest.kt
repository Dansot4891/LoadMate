package com.loadmate.app.core.pleos

import org.junit.Assert.assertEquals
import org.junit.Test

class RearDoorStateTest {
    @Test
    fun `값이 없으면 열림 여부를 모른다`() {
        assertEquals(null, RearDoorState().isOpen)
    }

    @Test
    fun `최소값이면 닫힘, 그보다 크면 열림 - 에뮬레이터 REAR 0~100`() {
        val s = RearDoorState(minPosition = 0, maxPosition = 100)
        assertEquals(false, s.copy(position = 0).isOpen)
        assertEquals(true, s.copy(position = 100).isOpen)
        assertEquals(true, s.copy(position = 1).isOpen)
    }

    @Test
    fun `범위 조회 전에는 0을 닫힘 기준으로 본다`() {
        assertEquals(false, RearDoorState(position = 0).isOpen)
        assertEquals(true, RearDoorState(position = 100).isOpen)
    }
}
