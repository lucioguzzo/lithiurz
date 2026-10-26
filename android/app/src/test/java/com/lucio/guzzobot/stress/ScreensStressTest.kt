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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Apre ogni schermata dell'app, una dopo l'altra e poi tutte di fila, con
 * l'app vera sotto: stesso ViewModel, stesso database, stessa navigazione.
 *
 * È il test che avrebbe preso il crash della modalità JARVIS: una schermata
 * che non si apre qui non si apre neanche sul telefono.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34])
class ScreensStressTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController

    private fun launch() {
        val app = ApplicationProvider.getApplicationContext<GuzzoBotApp>()
        compose.setContent {
            nav = rememberNavController()
            val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(app.container))
            GuzzoBotTheme(mode = ThemeMode.DARK) {
                GuzzoBotNavHost(viewModel = viewModel, sharedText = null, navController = nav)
            }
        }
        compose.waitForIdle()
    }

    private val everyRoute: List<String> =
        Routes::class.java.declaredFields
            .filter { it.type == String::class.java }
            .map { it.isAccessible = true; it.get(null) as String }
            .flatMap { route ->
                if (route == Routes.PROVIDER) Providers.defaults.map { "$route/${it.id}" } else listOf(route)
            }

    private fun open(route: String) {
        try {
            compose.runOnIdle { nav.navigate(route) }
            compose.waitForIdle()
        } catch (error: Throwable) {
            throw AssertionError("La schermata «$route» manda in crash l'app", error)
        }
    }

    @Test
    fun `ogni schermata si apre e si richiude`() {
        launch()
        everyRoute.forEach { route ->
            open(route)
            compose.runOnIdle { nav.popBackStack() }
            compose.waitForIdle()
        }
    }

    @Test
    fun `tutte le schermate impilate e poi chiuse a ritroso`() {
        launch()
        // Tre giri: lo stato che sopravvive a una schermata deve reggere
        // anche quando ci si torna sopra.
        repeat(3) { everyRoute.forEach(::open) }
        compose.runOnIdle { while (nav.popBackStack()) Unit }
        compose.waitForIdle()
    }
}
