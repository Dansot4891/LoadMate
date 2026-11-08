package com.loadmate.app.feature.trip.data

import com.loadmate.app.core.pleos.NaviConnection
import com.loadmate.app.core.pleos.NaviDestination
import com.loadmate.app.core.pleos.NaviEvent
import com.loadmate.app.core.pleos.NaviGateway
import com.loadmate.app.core.pleos.NaviState
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.feature.trip.domain.TripRepository
import com.loadmate.app.feature.trip.domain.TripStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class TripSessionTest {
    private val trip by lazy {
        val body = javaClass.classLoader!!.getResourceAsStream("trip/trip_in_progress.json")!!.bufferedReader().readText()
        TripJsonParser.trip(body)
    }
    private class FakeNavi : NaviGateway {
        override val state = MutableStateFlow(NaviState(connection = NaviConnection.Connected))
        var connections = 0
        var releases = 0
        override fun connect() { connections++ }
        override fun requestRoute(destination: NaviDestination, waypoints: List<NaviDestination>) = Unit
        override fun cancelRoute() = Unit
        override fun release() { releases++ }
    }
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `화면 수명과 관계없이 운행 종료 때 내비 리스너 해제`() {
        val current = MutableStateFlow<Trip?>(null)
        val repo = Proxy.newProxyInstance(TripRepository::class.java.classLoader, arrayOf(TripRepository::class.java)) { _, method, _ ->
            if (method.name == "getCurrentTrip") current else error("unexpected ${method.name}")
        } as TripRepository
        val navi = FakeNavi()
        TripSession(repo, navi, null) { _, _, _ -> }
        current.value = trip
        assertEquals(1, navi.connections)
        assertEquals(0, navi.releases)
        current.value = trip.copy(status = TripStatus.COMPLETED)
        assertEquals(1, navi.releases)
    }
}
