package com.lucio.guzzobot

import com.lucio.guzzobot.data.media.VoiceSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSetupTest {

    @Test
    fun `il pacchetto del motore di Google e' quello giusto`() {
        // Un refuso qui porterebbe a una scheda inesistente del Play Store.
        assertEquals("com.google.android.tts", VoiceSetup.GOOGLE_TTS)
    }

    @Test
    fun `gli indirizzi del Play Store sono costruiti bene`() {
        assertEquals(
            "market://details?id=com.google.android.tts",
            VoiceSetup.market(VoiceSetup.GOOGLE_TTS),
        )
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.google.android.tts",
            VoiceSetup.web(VoiceSetup.GOOGLE_TTS),
        )
    }

    @Test
    fun `si prova prima l'azione ufficiale di Android`() {
        assertEquals(
            VoiceSetup.Step.Action("android.speech.tts.engine.INSTALL_TTS_DATA"),
            VoiceSetup.installVoices().first(),
        )
    }

    @Test
    fun `l'ultima strada funziona ovunque ci sia un browser`() {
        listOf(
            VoiceSetup.installVoices(),
            VoiceSetup.openSettings(),
            VoiceSetup.googleEngine(),
        ).forEach { steps ->
            val last = steps.last()
            assertTrue(steps.toString(), last is VoiceSetup.Step.Link)
            assertTrue(
                steps.toString(),
                (last as VoiceSetup.Step.Link).uri.startsWith("https://"),
            )
        }
    }

    @Test
    fun `ogni percorso ha piu' di una strada, per non restare a mani vuote`() {
        listOf(
            VoiceSetup.installVoices(),
            VoiceSetup.openSettings(),
            VoiceSetup.googleEngine(),
        ).forEach { assertTrue(it.size >= 2) }
    }

    @Test
    fun `l'azione non pubblica non viene mai provata per prima da sola`() {
        // Le impostazioni di sintesi non fanno parte dell'API: se mancano,
        // dietro deve esserci sempre qualcos'altro.
        val settings = VoiceSetup.openSettings()
        assertTrue(settings.first() is VoiceSetup.Step.Action)
        assertTrue(settings.drop(1).isNotEmpty())
    }
}
