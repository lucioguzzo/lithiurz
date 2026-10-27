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

    // --- interrompere a frase in corso -------------------------------------

    @Test
    fun `un parziale vuoto non interrompe mai`() {
        // Era il guasto: all'inizio di una voce il parziale è sempre vuoto, e
        // la prima voce che il microfono sente è quella dell'altoparlante.
        assertFalse(EchoGuard.shouldCutIn("", speaking))
        assertFalse(EchoGuard.shouldCutIn("   ", speaking))
    }

    @Test
    fun `l'eco a meta' frase non interrompe, nemmeno storpiata`() {
        assertFalse(EchoGuard.shouldCutIn("la capitale della", speaking))
        // Il riconoscimento sbaglia le desinenze dell'eco.
        assertFalse(EchoGuard.shouldCutIn("la capitali della francia", speaking))
        assertFalse(EchoGuard.shouldCutIn("parigino una città di circo", speaking))
    }

    @Test
    fun `poche parole non bastano per interrompere a frase in corso`() {
        assertFalse(EchoGuard.shouldCutIn("basta così", speaking))
        assertTrue(EchoGuard.shouldCutIn("basta così grazie", speaking))
    }

    @Test
    fun `una persona che parla sopra interrompe`() {
        assertTrue(EchoGuard.shouldCutIn("aspetta dimmi invece di Roma", speaking))
        assertTrue(EchoGuard.shouldCutIn("fermati un attimo", speaking))
    }

    @Test
    fun `senza voce in lettura ogni frase di tre parole e' una persona`() {
        assertTrue(EchoGuard.shouldCutIn("fermati un attimo", null))
    }

    // --- togliere l'eco dalla frase dell'utente -----------------------------

    @Test
    fun `l'eco in testa alla frase si toglie`() {
        assertEquals(
            "aspetta dimmi di Roma",
            EchoGuard.withoutEcho("di circa due milioni aspetta dimmi di Roma", speaking),
        )
    }

    @Test
    fun `una frase tutta eco diventa vuota`() {
        assertEquals("", EchoGuard.withoutEcho("una città di circa due milioni", speaking))
    }

    @Test
    fun `senza eco la frase resta com'e'`() {
        assertEquals("Che tempo fa a Roma?", EchoGuard.withoutEcho("Che tempo fa a Roma?", speaking))
        assertEquals("Parigi quanti abitanti ha", EchoGuard.withoutEcho("Parigi quanti abitanti ha", ""))
        assertEquals("ciao", EchoGuard.withoutEcho("  ciao ", null))
    }

    @Test
    fun `si toglie solo la testa, non le parole dell'utente che ripetono la voce`() {
        // «Parigi» in mezzo alla domanda è dell'utente: resta.
        assertEquals(
            "quanti abitanti ha Parigi",
            EchoGuard.withoutEcho("abitanti quanti abitanti ha Parigi", speaking),
        )
    }
}
