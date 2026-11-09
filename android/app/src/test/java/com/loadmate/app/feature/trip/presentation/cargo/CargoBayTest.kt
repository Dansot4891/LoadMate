package com.loadmate.app.feature.trip.presentation.cargo

import com.loadmate.app.feature.trip.domain.CargoLine
import com.loadmate.app.feature.trip.domain.CargoSlot
import com.loadmate.app.feature.trip.domain.CargoUnit
import com.loadmate.app.feature.trip.domain.CargoUnitState
import com.loadmate.app.feature.trip.domain.Comparison
import com.loadmate.app.feature.trip.domain.ComparisonState
import com.loadmate.app.feature.trip.domain.Issue
import com.loadmate.app.feature.trip.domain.ObservationSource
import com.loadmate.app.feature.trip.domain.Stop
import com.loadmate.app.feature.trip.domain.StopStatus
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.domain.TripStatus
import com.loadmate.app.feature.trip.presentation.cargo.components.BayProjector
import com.loadmate.app.feature.trip.presentation.cargo.components.BoxSpec
import com.loadmate.app.feature.trip.presentation.cargo.components.DEFAULT_PITCH
import com.loadmate.app.feature.trip.presentation.cargo.components.DEFAULT_YAW
import com.loadmate.app.feature.trip.presentation.cargo.components.FaceKind
import com.loadmate.app.feature.trip.presentation.cargo.components.P2
import com.loadmate.app.feature.trip.presentation.cargo.components.centerOf
import com.loadmate.app.feature.trip.presentation.cargo.components.clampZoom
import com.loadmate.app.feature.trip.presentation.cargo.components.unloadAlpha
import com.loadmate.app.feature.trip.presentation.cargo.components.unloadLiftAmount
import com.loadmate.app.feature.trip.presentation.cargo.components.unloadSlideAmount
import com.loadmate.app.feature.trip.presentation.cargo.components.hitBox
import com.loadmate.app.feature.trip.presentation.cargo.components.visibleBoxFaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CargoBayTest {
    private val stopA = Stop("a", 1, "A", "판교", "", 0.0, 0.0, StopStatus.PENDING, null, listOf(CargoLine(1, "박스", "box", 3, 0, "rear")))

    private fun unit(id: Int, state: CargoUnitState, slot: CargoSlot?, planned: CargoSlot? = slot, source: ObservationSource? = null, seq: Int = id) =
        CargoUnit(id, "a", null, "box", "박스", id, state, seq, slot, planned, source)

    private fun trip(vararg units: CargoUnit) = Trip(
        "t", "샘플", "sample", TripStatus.IN_PROGRESS, null, listOf(stopA), null,
        Comparison(ComparisonState.NO_OBSERVATION, null, null, emptyList(), null, true, 300), units = units.toList(),
    )

    @Test fun `slot text names zone lane and floor`() {
        assertEquals("뒷문 쪽 오른쪽 2층", slotText(CargoSlot("rear", 2, 2)))
        assertEquals("가운데 중앙 1층", slotText(CargoSlot("middle", 1, 1)))
        assertEquals("자리 미정", slotText(null))
    }

    @Test fun `unit is misplaced only when loaded away from its plan`() {
        val plan = CargoSlot("rear", 0, 1)
        assertTrue(unit(1, CargoUnitState.LOADED, CargoSlot("front", 0, 1), plan).misplaced)
        assertFalse(unit(1, CargoUnitState.LOADED, plan, plan).misplaced)
        assertFalse(unit(1, CargoUnitState.PLANNED, null, plan).misplaced)
    }

    @Test fun `unload order takes top floor and latest loaded first`() {
        val t = trip(
            unit(1, CargoUnitState.LOADED, CargoSlot("rear", 0, 1), seq = 1),
            unit(2, CargoUnitState.LOADED, CargoSlot("rear", 1, 1), seq = 2),
            unit(3, CargoUnitState.LOADED, CargoSlot("rear", 1, 2), seq = 3),
            unit(4, CargoUnitState.UNLOADED, null, null),
        )
        assertEquals(listOf(3, 2, 1), unloadOrder(t, stopA).map { it.id })
    }

    @Test fun `legend counts boxes still on board per stop`() {
        val t = trip(
            unit(1, CargoUnitState.LOADED, CargoSlot("rear", 0, 1)),
            unit(2, CargoUnitState.PLANNED, null, CargoSlot("rear", 1, 1)),
            unit(3, CargoUnitState.UNLOADED, null, null),
        )
        assertEquals(listOf<Pair<String?, String>>("A" to "A 판교 2개"), stopLegend(t))
    }

    @Test fun `issue text names the slot and the box`() {
        val slot = CargoSlot("rear", 1, 2)
        val t = trip(unit(3, CargoUnitState.LOADED, slot))
        val issue = Issue(1, "shift", "rear", "", ObservationSource.SAMPLE, "2026-11-09T00:00:00Z", true, null, unitId = 3)
        assertEquals("뒷문 쪽 중앙 2층 · A 박스 3번 · 적재물 이동 의심", issueText(t, issue))
    }

    @Test fun `issue without a box names only the zone`() {
        val issue = Issue(1, "leak", "front", "", ObservationSource.SAMPLE, "2026-11-09T00:00:00Z", true, null)
        assertEquals("앞쪽 · 누액 의심", issueText(trip(), issue))
    }

    @Test fun `unload motion lifts first then slides and fades at the end`() {
        assertEquals(1f, unloadLiftAmount(0.3f), 0.001f)
        assertEquals(0f, unloadSlideAmount(0.3f), 0.001f)
        assertEquals(1f, unloadSlideAmount(1f), 0.001f)
        assertEquals(1f, unloadAlpha(0.5f), 0.001f)
        assertEquals(0f, unloadAlpha(1f), 0.001f)
    }

    @Test fun `zoom stays within limits`() {
        assertEquals(0.6f, clampZoom(0.1f), 0.001f)
        assertEquals(2.5f, clampZoom(9f), 0.001f)
    }

    @Test fun `preview order uses planned slots before loading and skips unloaded boxes`() {
        val t = trip(
            unit(1, CargoUnitState.PLANNED, null, CargoSlot("rear", 0, 1), seq = 1),
            unit(2, CargoUnitState.PLANNED, null, CargoSlot("rear", 0, 2), seq = 2),
            unit(3, CargoUnitState.UNLOADED, null, CargoSlot("rear", 1, 1), seq = 3),
        )
        assertEquals(listOf(2, 1), previewUnloadOrder(t, stopA).map { it.id })
    }

    @Test fun `zone counts include drawn units and skip unloaded ones`() {
        val t = trip(
            unit(1, CargoUnitState.LOADED, CargoSlot("rear", 0, 1)),
            unit(2, CargoUnitState.PLANNED, null, CargoSlot("rear", 1, 1)),
            unit(3, CargoUnitState.LOADED, CargoSlot("front", 0, 1)),
            unit(4, CargoUnitState.UNLOADED, null, CargoSlot("middle", 0, 1)),
        )
        assertEquals(listOf("front" to 1, "middle" to 0, "rear" to 2), zoneCounts(t))
    }

    private val projector = BayProjector(DEFAULT_YAW, DEFAULT_PITCH, 1200f, 880f)

    @Test fun `a box shows top and two sides from the default view`() {
        val faces = visibleBoxFaces(listOf(BoxSpec(1, 1, 1, 1)), projector)
        assertEquals(3, faces.size)
        assertEquals(1, faces.count { it.kind == FaceKind.TOP })
    }

    @Test fun `upper box is drawn after the box below it`() {
        val faces = visibleBoxFaces(listOf(BoxSpec(2, 1, 1, 2), BoxSpec(1, 1, 1, 1)), projector)
        assertEquals(1, faces.first().boxId)
        assertEquals(2, faces.last().boxId)
    }

    @Test fun `tap selects the front box and empty space selects nothing`() {
        val faces = visibleBoxFaces(listOf(BoxSpec(1, 1, 1, 1), BoxSpec(2, 1, 1, 2)), projector)
        val top = faces.last { it.boxId == 2 && it.kind == FaceKind.TOP }
        assertEquals(2, hitBox(faces, centerOf(top.points)))
        assertNull(hitBox(faces, P2(0f, 0f)))
    }
}
