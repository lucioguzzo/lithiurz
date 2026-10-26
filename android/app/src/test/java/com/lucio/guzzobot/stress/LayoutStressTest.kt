package com.lucio.guzzobot.stress

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.lucio.guzzobot.GuzzoBotApp
import com.lucio.guzzobot.data.model.Providers
import com.lucio.guzzobot.data.model.ThemeMode
import com.lucio.guzzobot.ui.GuzzoBotNavHost
import com.lucio.guzzobot.ui.Routes
import com.lucio.guzzobot.ui.chat.ChatViewModel
import com.lucio.guzzobot.ui.theme.GuzzoBotTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Ogni schermata su un telefono stretto e coi caratteri grandi — l'impostazione
 * che usa chi ci vede meno, cioè proprio chi un'app «non da smanettoni»
 * deve servire bene.
 */
@RunWith(RobolectricTestRunner::class)
// Grafica nativa: quella simulata misura ogni carattere un pixel, e ogni
// testo sembrerebbe schiacciato.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34])
class LayoutStressTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController

    private val routes = listOf(
        Routes.CHAT, Routes.SETTINGS, Routes.WORKSPACE, Routes.TASKS, Routes.AGENT, Routes.CODE,
        Routes.ARTIFACTS, Routes.CONNECTORS, Routes.PRIVACY, Routes.FREE, Routes.CATALOG,
        Routes.SKILLS, Routes.VOICE,
    ) + Providers.defaults.map { "${Routes.PROVIDER}/${it.id}" }

    private fun survey(): Map<String, List<String>> {
        val app = ApplicationProvider.getApplicationContext<GuzzoBotApp>()
        compose.setContent {
            nav = rememberNavController()
            val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(app.container))
            GuzzoBotTheme(mode = ThemeMode.LIGHT) {
                GuzzoBotNavHost(viewModel = viewModel, sharedText = null, navController = nav)
            }
        }
        compose.waitForIdle()
        return routes.associateWith { route ->
            if (route != Routes.CHAT) compose.runOnIdle { nav.navigate(route) }
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
            LayoutProbe.problems(compose).also {
                if (route != Routes.CHAT) compose.runOnIdle { nav.popBackStack() }
                compose.waitForIdle()
            }
        }.filterValues { it.isNotEmpty() }
    }

    private fun report(label: String, found: Map<String, List<String>>) {
        found.forEach { (route, list) -> list.forEach { println("LAYOUT $label $route: $it") } }
        assertTrue(
            "$label:\n" + found.entries.joinToString("\n") { (r, l) -> "$r → ${l.joinToString("; ")}" },
            found.isEmpty(),
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun `telefono stretto, caratteri grandi`() {
        RuntimeEnvironment.setFontScale(1.3f)
        report("360dp ×1.3", survey())
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun `caratteri al 150 per cento`() {
        RuntimeEnvironment.setFontScale(1.5f)
        report("360dp ×1.5", survey())
    }

    @Test
    @Config(qualifiers = "w393dp-h851dp")
    fun `caratteri al massimo di Android 14`() {
        RuntimeEnvironment.setFontScale(2.0f)
        report("393dp ×2.0", survey())
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `telefono comune, caratteri normali`() {
        report("411dp ×1.0", survey())
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp")
    fun `telefono piccolo`() {
        report("320dp ×1.0", survey())
    }
}
