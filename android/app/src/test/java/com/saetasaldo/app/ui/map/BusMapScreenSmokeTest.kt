package com.saetasaldo.app.ui.map

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.saetasaldo.app.domain.model.BusLine
import com.saetasaldo.app.domain.model.LineGroup
import com.saetasaldo.app.domain.model.MapConfig
import com.saetasaldo.app.domain.repository.BusMapRepository
import com.saetasaldo.app.ui.theme.SaetaSaldoTheme
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.LooperMode
import java.time.Duration

/**
 * Boots the map screen (selectors + osmdroid MapView) like a real navigation
 * so a wiring or inflation crash shows up as a test failure.
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class BusMapScreenSmokeTest {

    private val tree = LineGroup(
        codGrupo = "root",
        subGroups = listOf(
            LineGroup(
                codGrupo = "URBANO",
                subGroups = listOf(
                    LineGroup(
                        codGrupo = "Corredor 1",
                        lineas = listOf(BusLine("100", "1B"))
                    )
                )
            )
        )
    )

    @Test
    fun `bus map screen composes and attaches the osmdroid map view`() {
        val repository = mockk<BusMapRepository>()
        coEvery { repository.lineTree() } returns Result.success(tree)
        coEvery { repository.config() } returns Result.success(MapConfig.DEFAULT)
        val viewModel = BusMapViewModel(repository)

        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
            .create().start().resume().visible()
        try {
            controller.get().setContent {
                SaetaSaldoTheme {
                    BusMapScreen(viewModel = viewModel, onBackClick = {})
                }
            }
            Shadows.shadowOf(RuntimeEnvironment.getApplication().mainLooper)
                .idleFor(Duration.ofSeconds(5))
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
