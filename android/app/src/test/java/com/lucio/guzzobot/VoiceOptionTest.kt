package com.lucio.guzzobot

import android.speech.tts.Voice
import com.lucio.guzzobot.data.media.VoiceOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceOptionTest {

    @Test
    fun `la riga dice lingua, qualita' e se funziona offline`() {
        val voice = VoiceOption(
            id = "it-it-x-kda-local",
            locale = "Italiano (Italia)",
            quality = Voice.QUALITY_VERY_HIGH,
            offline = true,
        )
        assertEquals("Italiano (Italia) · qualità ottima · offline", voice.label)
        // Il nome tecnico non finisce sotto gli occhi di nessuno.
        assertFalse(voice.label, voice.label.contains("x-kda"))
    }

    @Test
    fun `una voce che richiede la rete non promette l'offline`() {
        val voice = VoiceOption("v", "Italiano", Voice.QUALITY_HIGH, offline = false)
        assertEquals("Italiano · qualità alta", voice.label)
    }

    @Test
    fun `i livelli del motore diventano parole`() {
        assertEquals("qualità ottima", VoiceOption.qualityLabel(Voice.QUALITY_VERY_HIGH))
        assertEquals("qualità alta", VoiceOption.qualityLabel(Voice.QUALITY_HIGH))
        assertEquals("qualità normale", VoiceOption.qualityLabel(Voice.QUALITY_NORMAL))
        assertEquals("qualità bassa", VoiceOption.qualityLabel(Voice.QUALITY_LOW))
        assertEquals("qualità minima", VoiceOption.qualityLabel(Voice.QUALITY_VERY_LOW))
    }

    @Test
    fun `un livello fuori scala non fa saltare l'etichetta`() {
        assertTrue(VoiceOption.qualityLabel(0).startsWith("qualità"))
        assertTrue(VoiceOption.qualityLabel(9999).startsWith("qualità"))
    }

    @Test
    fun `l'identificativo resta quello del motore, perche' va ridato a lui`() {
        val voice = VoiceOption("it-it-x-kda-local", "Italiano", Voice.QUALITY_HIGH, true)
        assertEquals("it-it-x-kda-local", voice.id)
    }
}
