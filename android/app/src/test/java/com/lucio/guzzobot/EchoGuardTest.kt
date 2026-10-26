package com.lucio.guzzobot

import com.lucio.guzzobot.data.media.EchoGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoGuardTest {

    private val speaking = "La capitale della Francia è Parigi, una città di circa due milioni di abitanti."

    @Test
    fun `sentire le proprie parole non e' una interruzione`() {
        // Il microfono sente l'altoparlante: senza questo, si interromperebbe
        // da solo a ogni frase.
        assertTrue(EchoGuard.isEcho("la capitale della francia", speaking))
        assertFalse(EchoGuard.shouldInterrupt("la capitale della francia", speaking))
    }

    @Test
    fun `la punteggiatura e le maiuscole non ingannano il confronto`() {
        assertTrue(EchoGuard.isEcho("È PARIGI!", speaking))
        assertTrue(EchoGuard.isEcho("una città, di circa due milioni", speaking))
    }

    @Test
    fun `una domanda vera interrompe`() {
        assertFalse(EchoGuard.isEcho("aspetta dimmi invece di Roma", speaking))
        assertTrue(EchoGuard.shouldInterrupt("aspetta dimmi invece di Roma", speaking))
    }

    @Test
    fun `una parola sola non zittisce una risposta a meta'`() {
        // Un rumore trascritto come «ah» non deve interrompere.
        assertFalse(EchoGuard.shouldInterrupt("ah", speaking))
        assertFalse(EchoGuard.shouldInterrupt("basta", speaking))
        // Due parole sì, se non sono eco.
        assertTrue(EchoGuard.shouldInterrupt("basta così", speaking))
    }

    @Test
    fun `quando non si sta leggendo niente, tutto e' voce di una persona`() {
        assertFalse(EchoGuard.isEcho("qualunque cosa", null))
        assertFalse(EchoGuard.isEcho("qualunque cosa", ""))
        assertTrue(EchoGuard.shouldInterrupt("qualunque cosa", null))
    }

    @Test
    fun `un silenzio trascritto non interrompe`() {
        assertFalse(EchoGuard.shouldInterrupt("", speaking))
        assertFalse(EchoGuard.shouldInterrupt("   ", speaking))
        assertFalse(EchoGuard.isEcho("", speaking))
    }

    @Test
    fun `parole miste contano per quante ne combaciano`() {
        // Quasi tutte dall'eco: non è una persona che parla.
        assertTrue(EchoGuard.isEcho("capitale francia parigi domani", speaking))
        // Quasi nessuna: lo è.
        assertFalse(EchoGuard.isEcho("domani piove a Milano credo", speaking))
    }

    @Test
    fun `la normalizzazione tiene solo le parole`() {
        assertEquals("ciao come stai", EchoGuard.normalize("  Ciao, come  stai? "))
    }
}
