package com.lucio.guzzobot.stress

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.lucio.guzzobot.GuzzoBotApp
import com.lucio.guzzobot.MainActivity
import com.lucio.guzzobot.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSpeechRecognizer
import org.robolectric.shadows.ShadowTextToSpeech
import kotlin.random.Random

/**
 * L'app intera, dall'icona in poi: primo avvio, conversazione a voce, e
 * centinaia di tocchi a caso. Ogni eccezione che arriva fin qui è un crash
 * che sul telefono chiuderebbe GUZZOBOT.
 */
@RunWith(RobolectricTestRunner::class)
// Grafica nativa: con quella simulata i testi misurano un pixel a carattere
// e certi pulsanti risultano grandi zero, quindi intoccabili.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34])
class AppStressTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<GuzzoBotApp>()

    private fun text(id: Int) = app.getString(id)

    /** Le impostazioni arrivano dal disco in un altro thread: si aspetta che la schermata ci sia. */
    private fun await(label: String, substring: Boolean = false) {
        try {
            compose.waitUntil(timeoutMillis = 15_000) {
                compose.onAllNodesWithText(label, substring = substring).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: Throwable) {
            throw AssertionError("Non compare «$label». Sullo schermo:\n" + screen(), error)
        }
    }

    private fun screen(): String = runCatching {
        compose.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().joinToString("\n---\n") { root ->
            buildList {
                fun walk(node: androidx.compose.ui.semantics.SemanticsNode) {
                    node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }
                        ?.joinToString(" ")?.let(::add)
                    node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) { null }
                        ?.joinToString(" ")?.let { add("[$it]") }
                    node.children.forEach(::walk)
                }
                walk(root)
            }.joinToString(" | ")
        }
    }.getOrElse { "(illeggibile: $it)" }

    private fun awaitMenu() {
        try {
            compose.waitUntil(timeoutMillis = 15_000) {
                compose.onAllNodesWithContentDescription(text(R.string.menu)).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: Throwable) {
            throw AssertionError("Non torna alla chat. Sullo schermo:\n" + screen(), error)
        }
    }

    /** Lo splash si chiude da sé dopo un secondo e mezzo: si fa passare il tempo. */
    private fun pastSplash() {
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
    }

    private fun skipOnboarding() = runBlocking { app.container.settingsStore.completeOnboarding() }

    /** Fa credere ad Android che il telefono sappia riconoscere la voce. */
    private fun installRecognizer() {
        val info = ResolveInfo().apply {
            serviceInfo = ServiceInfo().apply {
                packageName = "com.google.android.tts"
                name = "FakeRecognizer"
            }
        }
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent(RecognitionService.SERVICE_INTERFACE), info)
        shadowOf(app.packageManager).addServiceIfNotPresent(ComponentName("com.google.android.tts", "FakeRecognizer"))
    }

    /** Il motore di sintesi risponde «pronto», come fa sul telefono dopo un attimo. */
    private fun ttsReady() {
        val tts = ShadowTextToSpeech.getLastTextToSpeechInstance() ?: return
        shadowOf(tts).onInitListener?.onInit(TextToSpeech.SUCCESS)
    }

    @org.junit.Before
    fun reset() {
        runBlocking { app.container.settingsStore.clearAll() }
        // FileProvider ricorda in un campo statico le cartelle del primo test;
        // Robolectric ne crea una nuova a ogni test, e la foto della fotocamera
        // finirebbe «fuori» da una cartella che non esiste più. Sul telefono la
        // cartella dell'app non cambia mai: è solo un artefatto dei test.
        runCatching {
            val cache = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            cache.isAccessible = true
            (cache.get(null) as MutableMap<*, *>).clear()
        }
    }

    @Test
    fun `primo avvio completo, poi JARVIS dal menu`() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        installRecognizer()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            ttsReady()
            pastSplash()
            await(text(R.string.onboarding_start))
            compose.onNodeWithText(text(R.string.onboarding_start)).performScrollTo().performClick()
            await(text(R.string.onboarding_later))
            compose.onNodeWithText(text(R.string.onboarding_later)).performScrollTo().performClick()
            await(text(R.string.onboarding_done))
            compose.onNodeWithText(text(R.string.onboarding_done)).performScrollTo().performClick()
            // Il primo avvio si ritira quando il flag è scritto su disco.
            compose.waitUntil(timeoutMillis = 15_000) {
                compose.onAllNodesWithText(text(R.string.onboarding_done)).fetchSemanticsNodes().isEmpty()
            }
            awaitMenu()

            compose.onNodeWithContentDescription(text(R.string.menu)).performClick()
            pastSplash()
            await(text(R.string.voice_conversation))
            compose.onAllNodesWithText(text(R.string.voice_conversation))[0].performClick()
            await(text(R.string.voice_leave), substring = true)

            // JARVIS deve essersi messo in ascolto da solo.
            val recognizer = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
                ?: throw AssertionError("JARVIS non ha aperto il microfono")

            // L'utente parla: la frase diventa un messaggio. Senza nessun
            // modello configurato la risposta è un errore spiegato — non un crash.
            shadowOf(recognizer).triggerOnResults(
                Bundle().apply {
                    putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("Ciao, come ti chiami?"))
                },
            )
            compose.waitForIdle()

            // Un silenzio, un errore del riconoscimento, poi l'uscita.
            ShadowSpeechRecognizer.getLatestSpeechRecognizer()?.let {
                shadowOf(it).triggerOnError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            }
            compose.waitForIdle()
            compose.onNodeWithText(text(R.string.voice_leave), substring = true).performClick()
            compose.waitForIdle()
        }
    }

    @Test
    fun `entrare e uscire da JARVIS cinquanta volte`() {
        skipOnboarding()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        installRecognizer()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            ttsReady()
            pastSplash()
            awaitMenu()
            repeat(50) {
                compose.onNodeWithContentDescription(text(R.string.menu)).performClick()
                pastSplash()
                await(text(R.string.voice_conversation))
                compose.onAllNodesWithText(text(R.string.voice_conversation))[0].performClick()
                await(text(R.string.voice_leave), substring = true)
                compose.onNodeWithText(text(R.string.voice_leave), substring = true).performClick()
                awaitMenu()
            }
        }
    }

    @Test
    fun `scimmia, primo avvio`() = monkey(seed = 1, steps = 400, onboarded = false)

    @Test
    fun `scimmia, app configurata`() = monkey(seed = 7, steps = 600, onboarded = true)

    @Test
    fun `scimmia, altro giro`() = monkey(seed = 2026, steps = 600, onboarded = true)

    /**
     * Tocca a caso tutto ciò che si può toccare, scrive nei campi, preme
     * indietro e ruota lo schermo. Seme fisso: un crash trovato si ripete
     * identico, e quindi si può correggere.
     */
    private fun monkey(seed: Int, steps: Int, onboarded: Boolean) {
        if (onboarded) skipOnboarding()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        installRecognizer()
        val random = Random(seed)
        val words = listOf("ciao", "", "https://example.com", "sk-test-0000", "🙂🙂", "a".repeat(5_000), "# Titolo\n```kotlin\nval x = 1\n```")
        var scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        ttsReady()
        pastSplash()
        if (onboarded) awaitMenu() else await(text(R.string.onboarding_start))
        var done = 0
        var hits = 0
        try {
            repeat(steps) { step ->
                done = step
                if (scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED) {
                    // Indietro dalla chat chiude l'app: si riapre, come farebbe l'utente.
                    scenario = ActivityScenario.launch(MainActivity::class.java)
                }
                when (random.nextInt(100)) {
                    in 0..7 -> scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                    in 8..9 -> scenario.recreate()
                    in 10..19 -> {
                        val fields = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size
                        if (fields > 0) {
                            tolerant { compose.onAllNodes(hasSetTextAction())[random.nextInt(fields)].performTextInput(words.random(random)) }
                        }
                    }
                    else -> {
                        val buttons = compose.onAllNodes(clickable).fetchSemanticsNodes().size
                        if (buttons > 0) {
                            tolerant {
                                compose.onAllNodes(clickable)[random.nextInt(buttons)].performClick()
                                hits++
                            }
                        }
                    }
                }
                compose.waitForIdle()
            }
        } catch (error: Throwable) {
            throw AssertionError("Crash al passo $done (seme $seed)", error)
        } finally {
            scenario.close()
        }
        assertEquals(steps - 1, done)
        // Una scimmia che non ha toccato niente non ha provato niente.
        println("Scimmia $seed: $hits tocchi riusciti su $steps passi")
        assertTrue("Solo $hits tocchi riusciti", hits > steps / 3)
    }

    private val clickable: SemanticsMatcher = hasClickAction() and isEnabled()

    /**
     * Un nodo sparito tra la conta e il tocco è un limite del test, non un
     * crash dell'app: quelli il framework li segnala come AssertionError.
     */
    private inline fun tolerant(action: () -> Unit) {
        try {
            action()
        } catch (_: AssertionError) {
        } catch (_: IllegalArgumentException) {
        }
    }
}
