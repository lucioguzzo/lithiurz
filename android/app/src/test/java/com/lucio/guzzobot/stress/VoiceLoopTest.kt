package com.lucio.guzzobot.stress

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.lucio.guzzobot.GuzzoBotApp
import com.lucio.guzzobot.data.media.VoiceStage
import com.lucio.guzzobot.ui.chat.ChatViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSpeechRecognizer
import org.robolectric.shadows.ShadowTextToSpeech

/**
 * Il giro di JARVIS mentre la voce legge, col microfono aperto sopra.
 *
 * Il guasto da cui nasce: la voce si zittiva da sola dopo un istante, perché
 * l'inizio di una voce qualunque — la sua, dall'altoparlante — bastava a
 * interromperla.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class VoiceLoopTest {

    private val app get() = ApplicationProvider.getApplicationContext<GuzzoBotApp>()
    private val speaker get() = app.container.deviceSpeaker
    private lateinit var viewModel: ChatViewModel

    private val answer = "La capitale della Francia è Parigi, una città di circa due milioni di abitanti."

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setUp() {
        runBlocking { app.container.settingsStore.clearAll() }
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val info = ResolveInfo().apply {
            serviceInfo = ServiceInfo().apply {
                packageName = "com.google.android.tts"
                name = "FakeRecognizer"
            }
        }
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent(RecognitionService.SERVICE_INTERFACE), info)
        shadowOf(app.packageManager).addServiceIfNotPresent(ComponentName("com.google.android.tts", "FakeRecognizer"))
        viewModel = ViewModelProvider(ViewModelStore(), ChatViewModel.factory(app.container))[ChatViewModel::class.java]
        // Il motore di sintesi risponde «pronto».
        shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()!!).onInitListener.onInit(TextToSpeech.SUCCESS)
        idle()
        viewModel.toggleVoiceMode()
        idle()
        assertTrue("JARVIS non è partito", viewModel.voiceMode.value)
    }

    /**
     * La voce comincia a leggere, come quando arriva la risposta.
     *
     * Il motore simulato completa ogni frase appena il thread principale
     * gira; una frase in più nel contatore la tiene «in lettura», come una
     * risposta lunga di cui restano altre frasi in coda.
     */
    private fun startSpeaking() {
        // L'utente ha finito la sua frase: il microfono della domanda è chiuso.
        mic().triggerOnEndOfSpeech()
        speaker.speak(answer, queue = true)
        val pending = speaker.javaClass.getDeclaredField("pending").apply { isAccessible = true }
        (pending.get(speaker) as java.util.concurrent.atomic.AtomicInteger).incrementAndGet()
        idle()
        assertTrue(speaker.speaking.value)
        assertEquals(VoiceStage.SPEAKING, viewModel.stage.value)
    }

    /**
     * La voce finisce. Sul telefono l'avviso arriva da un thread del motore di
     * sintesi, non da quello principale: per un attimo la voce risulta finita
     * ma l'app non è ancora tornata ad ascoltare. È lì che arriva l'eco in
     * ritardo.
     */
    private fun finishSpeaking() {
        val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()!!
        Thread { shadowOf(tts).utteranceProgressListener.onDone("frase") }.apply { start(); join() }
        assertFalse(speaker.speaking.value)
    }

    private fun mic() = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer()!!)

    private fun results(text: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
    }

    @Test
    fun `il microfono si riapre mentre la voce legge`() {
        val before = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        startSpeaking()
        val during = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        assertTrue("Il microfono non si è riaperto sopra la voce", during !== before)
    }

    @Test
    fun `la propria voce sentita dal microfono non la zittisce`() {
        startSpeaking()
        // Il microfono sente cominciare una voce: è quella dell'altoparlante.
        // Era questo segnale, da solo, a zittirla.
        val listener = app.container.deviceListener.javaClass.getDeclaredField("listener")
            .apply { isAccessible = true }
            .get(app.container.deviceListener) as android.speech.RecognitionListener
        listener.onBeginningOfSpeech()
        assertTrue("Si è zittita appena il microfono ha sentito una voce", speaker.speaking.value)
        // Il riconoscitore trascrive l'altoparlante, parola dopo parola, con
        // gli errori tipici dell'eco.
        listOf("la", "la capitale", "la capitali della", "la capitali della francia è parigi").forEach {
            mic().triggerOnPartialResults(results(it))
            assertTrue("Si è zittita da sola sentendo «$it»", speaker.speaking.value)
        }
        // E la frase finita, se è eco, non diventa una domanda.
        mic().triggerOnResults(results("la capitale della francia è parigi"))
        assertTrue(speaker.speaking.value)
        assertEquals("", viewModel.input.value)
    }

    @Test
    fun `una persona che parla sopra la zittisce`() {
        startSpeaking()
        mic().triggerOnPartialResults(results("aspetta fermati un attimo"))
        assertFalse("Non si è fermata per l'utente", speaker.speaking.value)
        assertEquals(VoiceStage.LISTENING, viewModel.stage.value)
    }

    @Test
    fun `l'eco arrivata dopo la fine della voce non diventa una domanda`() {
        startSpeaking()
        val overSpeech = mic()
        finishSpeaking()
        // Il riconoscitore aperto sopra la voce consegna l'eco in ritardo.
        overSpeech.triggerOnResults(results("una città di circa due milioni di abitanti"))
        assertTrue(viewModel.voiceMode.value)
        assertFalse("Ha mandato la propria voce come domanda", viewModel.stage.value == VoiceStage.THINKING)
    }

    @Test
    fun `un errore del microfono a voce appena finita non chiude JARVIS`() {
        startSpeaking()
        val overSpeech = mic()
        finishSpeaking()
        overSpeech.triggerOnError(SpeechRecognizer.ERROR_NO_MATCH)
        assertTrue("JARVIS si è chiuso da solo", viewModel.voiceMode.value)
    }
}
