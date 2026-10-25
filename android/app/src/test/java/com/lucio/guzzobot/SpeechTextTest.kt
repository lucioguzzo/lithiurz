package com.lucio.guzzobot

import com.lucio.guzzobot.data.media.SpeechText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {

    @Test
    fun `il codice si annuncia, non si legge`() {
        val spoken = SpeechText.clean(
            """
            Ecco la funzione:

            ```kotlin
            fun somma(a: Int, b: Int) = a + b
            ```

            Chiamala così.
            """.trimIndent(),
        )
        assertTrue(spoken, spoken.contains("blocco di codice"))
        assertFalse(spoken, spoken.contains("fun somma"))
        assertTrue(spoken, spoken.contains("Chiamala così."))
    }

    @Test
    fun `di un link si legge il testo, non l'indirizzo`() {
        assertEquals(
            "Vedi la documentazione per i dettagli.",
            SpeechText.clean("Vedi [la documentazione](https://esempio.test/a/b) per i dettagli."),
        )
        assertTrue(SpeechText.clean("Apri https://esempio.test/x").contains("(link)"))
    }

    @Test
    fun `i segni del markdown spariscono, le parole restano`() {
        val spoken = SpeechText.clean(
            """
            ## Titolo

            Questo è **importante** e questo è *meno*.

            - primo
            - secondo

            > citazione
            """.trimIndent(),
        )
        assertFalse(spoken, spoken.contains("#"))
        assertFalse(spoken, spoken.contains("*"))
        assertFalse(spoken, spoken.contains(">"))
        assertTrue(spoken, spoken.contains("Titolo"))
        assertTrue(spoken, spoken.contains("importante"))
        assertTrue(spoken, spoken.contains("primo"))
        assertTrue(spoken, spoken.contains("citazione"))
    }

    @Test
    fun `una tabella si annuncia invece di essere sillabata`() {
        val spoken = SpeechText.clean("| a | b |\n|---|---|\n| 1 | 2 |")
        assertTrue(spoken, spoken.contains("tabella"))
        assertFalse(spoken, spoken.contains("|"))
    }

    @Test
    fun `si leggono solo le frasi finite`() {
        val (ready, rest) = SpeechText.take("Buongiorno. Come stai? Sto scriv")
        assertEquals(listOf("Buongiorno.", "Come stai?"), ready)
        assertEquals(" Sto scriv", rest)
    }

    @Test
    fun `a risposta finita si legge anche l'ultimo pezzo`() {
        val (ready, rest) = SpeechText.take("Ecco il resto senza punto", flush = true)
        assertEquals(listOf("Ecco il resto senza punto"), ready)
        assertEquals("", rest)
    }

    @Test
    fun `un numero decimale non viene spezzato in due frasi`() {
        val (ready, _) = SpeechText.take("La versione 3.5 è uscita. ")
        assertEquals(listOf("La versione 3.5 è uscita."), ready)
    }

    @Test
    fun `l'a capo chiude una frase anche senza punto`() {
        val (ready, rest) = SpeechText.take("Primo punto\nSecondo punto\nterzo")
        assertEquals(listOf("Primo punto", "Secondo punto"), ready)
        assertEquals("terzo", rest)
    }

    @Test
    fun `un testo senza niente di finito non fa parlare a vuoto`() {
        val (ready, rest) = SpeechText.take("Sto ancora pensando")
        assertTrue(ready.isEmpty())
        assertEquals("Sto ancora pensando", rest)
        assertTrue(SpeechText.take("", flush = true).first.isEmpty())
        assertTrue(SpeechText.take("   ", flush = true).first.isEmpty())
    }
}
