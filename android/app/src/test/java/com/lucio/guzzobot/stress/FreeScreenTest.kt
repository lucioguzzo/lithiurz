package com.lucio.guzzobot.stress

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.lucio.guzzobot.GuzzoBotApp
import com.lucio.guzzobot.data.model.ModelCatalog
import com.lucio.guzzobot.data.model.Providers
import com.lucio.guzzobot.data.model.ThemeMode
import com.lucio.guzzobot.ui.GuzzoBotNavHost
import com.lucio.guzzobot.ui.Routes
import com.lucio.guzzobot.ui.chat.ChatViewModel
import com.lucio.guzzobot.ui.theme.GuzzoBotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * GLM-4.7-Flash è gratis da Z.ai, ma una scheda che dice solo «una parte dei
 * modelli è gratuita» non lo fa sapere a nessuno: va scritto, e attivando
 * Z.ai deve partire proprio quello.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w411dp-h4000dp")
class FreeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `la scheda di Z_ai dice quali modelli sono gratis`() {
        val app = ApplicationProvider.getApplicationContext<GuzzoBotApp>()
        lateinit var nav: NavHostController
        compose.setContent {
            nav = rememberNavController()
            val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(app.container))
            GuzzoBotTheme(mode = ThemeMode.LIGHT) {
                GuzzoBotNavHost(viewModel = viewModel, sharedText = null, navController = nav)
            }
        }
        compose.runOnIdle { nav.navigate(Routes.FREE) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val line = compose.onAllNodesWithText("glm-4.7-flash", substring = true).fetchSemanticsNodes()
        assertTrue("GLM-4.7-Flash non è nominato fra i gratuiti", line.isNotEmpty())
        val marker = compose.onAllNodesWithText(":free", substring = true).fetchSemanticsNodes()
        assertTrue("OpenRouter non dice come riconoscere i gratuiti", marker.isNotEmpty())
    }

    @Test
    fun `attivando Z_ai parte GLM-4_7-Flash`() {
        val zai = Providers.defaults.first { it.id == "zai" }
        val free = zai.models.filter { zai.freeTier!!.covers(it) }
        assertEquals("glm-4.7-flash", ModelCatalog.preferredChat(free, zai.models))
        // E un modello Flash a pagamento non passa per gratuito.
        assertTrue(!zai.freeTier!!.covers("glm-5.3-flash"))
    }
}
