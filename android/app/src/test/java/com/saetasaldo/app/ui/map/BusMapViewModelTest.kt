package com.saetasaldo.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.saetasaldo.app.domain.model.BusLine
import com.saetasaldo.app.domain.model.BusPosition
import com.saetasaldo.app.domain.model.LineGroup
import com.saetasaldo.app.domain.model.LineRoute
import com.saetasaldo.app.domain.model.MapConfig
import com.saetasaldo.app.domain.repository.BusMapRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class BusMapViewModelTest {

    private val repository = mockk<BusMapRepository>()

    private var now = 1_000_000L
    private val clock: () -> Long = { now }

    private val tree = LineGroup(
        codGrupo = "root",
        subGroups = listOf(
            LineGroup(
                codGrupo = "URBANO",
                subGroups = listOf(
                    LineGroup(
                        codGrupo = "Corredor 1",
                        lineas = listOf(BusLine("100", "1B"), BusLine("101", "1A"))
                    ),
                    LineGroup(
                        codGrupo = "Corredor 2",
                        lineas = listOf(BusLine("200", "2A"))
                    )
                )
            ),
            LineGroup(
                codGrupo = "TRONCALES",
                lineas = listOf(BusLine("500", "T2"))
            )
        )
    )

    private val fix132 = BusPosition(
        interno = "132",
        latitud = -24.8571,
        longitud = -65.4324,
        orientacion = 186.0,
        proximaParada = "Leguizamon y Dean Funes",
        vehiculoRampa = false
    )

    @Before
    fun setUp() {
        now = 1_000_000L
        coEvery { repository.lineTree() } returns Result.success(tree)
        coEvery { repository.config() } returns Result.success(MapConfig.DEFAULT)
        coEvery { repository.route(any()) } returns Result.success(LineRoute(emptyList()))
        coEvery { repository.lineNews(any()) } returns Result.success(emptyList())
        coEvery { repository.positions(any()) } returns Result.success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.newViewModel(): BusMapViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = BusMapViewModel(repository, clock, pollIntervalMs = 5_000L)
        runCurrent()
        return vm
    }

    private fun TestScope.selectLine1B(vm: BusMapViewModel) {
        vm.selectGroup(0, "URBANO")
        vm.selectGroup(1, "Corredor 1")
        vm.selectLine("100")
        runCurrent()
    }

    @Test
    fun `init loads line tree and config`() = runTest {
        val vm = newViewModel()

        assertEquals(tree, vm.uiState.value.lineTree)
        assertEquals(MapConfig.DEFAULT, vm.uiState.value.config)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `tree failure surfaces a spanish error`() = runTest {
        coEvery { repository.lineTree() } returns Result.failure(IOException("offline"))
        val vm = newViewModel()

        assertNull(vm.uiState.value.lineTree)
        assertTrue(vm.uiState.value.error != null)
    }

    @Test
    fun `selection walks nested groups to reach a line`() = runTest {
        val vm = newViewModel()
        try {
            vm.selectGroup(0, "URBANO")
            vm.selectGroup(1, "Corredor 1")
            vm.selectLine("100")

            assertEquals(listOf("URBANO", "Corredor 1"), vm.uiState.value.selectionPath)
            assertEquals(BusLine("100", "1B"), vm.uiState.value.selectedLine)
        } finally {
            vm.stopPolling()
        }
    }

    @Test
    fun `group with direct lineas selects a line without deeper groups`() = runTest {
        val vm = newViewModel()
        try {
            vm.selectGroup(0, "TRONCALES")
            vm.selectLine("500")

            assertEquals(listOf("TRONCALES"), vm.uiState.value.selectionPath)
            assertEquals(BusLine("500", "T2"), vm.uiState.value.selectedLine)
        } finally {
            vm.stopPolling()
        }
    }

    @Test
    fun `reselecting an earlier level truncates path and clears line`() = runTest {
        val vm = newViewModel()
        selectLine1B(vm)
        coVerify(atLeast = 1) { repository.positions("100") }

        vm.selectGroup(1, "Corredor 2")
        runCurrent()

        assertEquals(listOf("URBANO", "Corredor 2"), vm.uiState.value.selectionPath)
        assertNull(vm.uiState.value.selectedLine)
        assertNull(vm.uiState.value.route)
        assertTrue(vm.uiState.value.buses.isEmpty())
    }

    @Test
    fun `invalid group selection is ignored`() = runTest {
        val vm = newViewModel()

        vm.selectGroup(0, "NO_EXISTE")

        assertTrue(vm.uiState.value.selectionPath.isEmpty())
    }

    @Test
    fun `line outside current group is ignored`() = runTest {
        val vm = newViewModel()
        vm.selectGroup(0, "URBANO")
        vm.selectGroup(1, "Corredor 1")

        vm.selectLine("500")

        assertNull(vm.uiState.value.selectedLine)
    }

    @Test
    fun `selectLine loads route and news once then polls`() = runTest {
        val vm = newViewModel()
        try {
            selectLine1B(vm)

            coVerify(exactly = 1) { repository.route("100") }
            coVerify(exactly = 1) { repository.lineNews("100") }
            coVerify(exactly = 1) { repository.positions("100") }
        } finally {
            vm.stopPolling()
        }
    }

    @Test
    fun `poll fires positions fetch at the configured interval`() = runTest {
        val vm = newViewModel()
        try {
            selectLine1B(vm)
            coVerify(exactly = 1) { repository.positions("100") }

            advanceTimeBy(11_000L)

            coVerify(atLeast = 3) { repository.positions("100") }
        } finally {
            vm.stopPolling()
        }
    }

    @Test
    fun `clearSelection cancels the poll loop`() = runTest {
        val vm = newViewModel()
        selectLine1B(vm)
        coVerify(exactly = 1) { repository.positions("100") }

        vm.clearSelection()
        advanceTimeBy(60_000L)

        coVerify(exactly = 1) { repository.positions("100") }
        assertNull(vm.uiState.value.selectedLine)
        assertEquals(tree, vm.uiState.value.lineTree)
    }

    @Test
    fun `onCleared cancels the poll loop`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val provider = ViewModelProvider(store, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return BusMapViewModel(repository, clock, 5_000L) as T
            }
        })
        val vm = provider[BusMapViewModel::class.java]
        runCurrent()
        vm.selectGroup(0, "URBANO")
        vm.selectGroup(1, "Corredor 1")
        vm.selectLine("100")
        runCurrent()
        coVerify(exactly = 1) { repository.positions("100") }

        store.clear()
        advanceTimeBy(60_000L)

        coVerify(exactly = 1) { repository.positions("100") }
    }

    @Test
    fun `failed fetches back off exponentially up to the cap`() = runTest {
        coEvery { repository.positions("100") } returns Result.failure(IOException("offline"))
        val vm = newViewModel()
        try {
            selectLine1B(vm)
            // t=0 fetch#1 -> delay 10s; t=10s fetch#2 -> delay 20s; t=30s fetch#3 -> delay 40s; t=70s fetch#4
            coVerify(exactly = 1) { repository.positions("100") }

            advanceTimeBy(10_500L)
            coVerify(exactly = 2) { repository.positions("100") }

            advanceTimeBy(20_500L)
            coVerify(exactly = 3) { repository.positions("100") }

            advanceTimeBy(40_500L)
            coVerify(exactly = 4) { repository.positions("100") }
        } finally {
            vm.stopPolling()
        }
    }

    @Test
    fun `a successful fetch resets the backoff`() = runTest {
        var fail = true
        coEvery { repository.positions("100") } answers {
            if (fail) Result.failure(IOException("offline")) else Result.success(listOf(fix132))
        }
        val vm = newViewModel()
        try {
            selectLine1B(vm)
            coVerify(exactly = 1) { repository.positions("100") }
            advanceTimeBy(10_500L) // second fetch fails -> backoff 20s
            coVerify(exactly = 2) { repository.positions("100") }

            fail = false
            advanceTimeBy(20_500L) // third fetch succeeds -> next at +5s
            coVerify(exactly = 3) { repository.positions("100") }

            advanceTimeBy(5_500L)
            coVerify(exactly = 4) { repository.positions("100") }
        } finally {
            vm.stopPolling()
        }
    }

    @Test
    fun `lastChangeAt only advances when the fix actually changes`() = runTest {
        var position = fix132
        coEvery { repository.positions("100") } answers { Result.success(listOf(position)) }
        val vm = newViewModel()
        try {
            selectLine1B(vm)

            val first = vm.uiState.value.buses.single()
            assertEquals(now, first.lastChangeAtMs)

            now += 40_000L
            advanceTimeBy(5_100L) // same fix again
            val unchanged = vm.uiState.value.buses.single()
            assertEquals(first.lastChangeAtMs, unchanged.lastChangeAtMs)
            assertEquals(40_000L, unchanged.ageSeconds(now) * 1000L)

            position = fix132.copy(latitud = fix132.latitud + 0.001)
            advanceTimeBy(5_100L) // moved fix
            val moved = vm.uiState.value.buses.single()
            assertEquals(now, moved.lastChangeAtMs)
        } finally {
            vm.stopPolling()
        }
    }

    @Test
    fun `buses missing from a fetch keep their last position`() = runTest {
        var positions = listOf(fix132)
        coEvery { repository.positions("100") } answers { Result.success(positions) }
        val vm = newViewModel()
        try {
            selectLine1B(vm)
            assertEquals(1, vm.uiState.value.buses.size)

            positions = emptyList()
            now += 5_100L
            advanceTimeBy(5_100L)

            val stale = vm.uiState.value.buses.single()
            assertEquals("132", stale.position.interno)
            assertTrue(stale.ageSeconds(now) >= 5L)
        } finally {
            vm.stopPolling()
        }
    }

    @Test
    fun `empty positions list is not an error`() = runTest {
        val vm = newViewModel()
        try {
            selectLine1B(vm)

            assertNull(vm.uiState.value.error)
            assertTrue(vm.uiState.value.buses.isEmpty())
            assertTrue(vm.uiState.value.lastPollAt != null)
        } finally {
            vm.stopPolling()
        }
    }
}
