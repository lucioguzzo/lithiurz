package com.lucio.guzzobot.data.media

/**
 * Distinguere chi parla.
 *
 * Per poter interrompere la voce basta tenere il microfono aperto mentre
 * legge. Il guaio è che il microfono sente anche l'altoparlante: senza un
 * controllo, GUZZOBOT trascriverebbe sé stesso e si interromperebbe da solo
 * a ogni frase.
 *
 * Qui si confronta quello che il riconoscitore sta sentendo con quello che
 * l'app sta dicendo in quel momento: se combaciano, è eco e va ignorato. Non
 * è perfetto — con l'altoparlante a volume alto niente lo è — ma toglie il
 * caso che capita sempre, cioè sentirsi ripetere le proprie parole.
 */
object EchoGuard {

    /** Sotto questa soglia di somiglianza è voce di una persona, non eco. */
    private const val SIMILAR = 0.6

    /** Troppo corto per decidere: meglio non interrompere per un «eh». */
    private const val MIN_WORDS = 2

    /**
     * Vero se quello che si sente è probabilmente la voce dell'app.
     *
     * @param heard quello che il riconoscitore ha capito finora
     * @param speaking il testo che il sintetizzatore sta leggendo
     */
    fun isEcho(heard: String, speaking: String?): Boolean {
        val voice = normalize(speaking.orEmpty())
        val words = normalize(heard)
        if (voice.isEmpty() || words.isEmpty()) return false
        if (voice.contains(words)) return true
        return overlap(words, voice) >= SIMILAR
    }

    /**
     * Vero se vale la pena interrompere la lettura per quello che si sente.
     *
     * Una parola sola presa a caso dall'eco, o un rumore trascritto come
     * «ah», non devono zittire una risposta a metà.
     */
    fun shouldInterrupt(heard: String, speaking: String?): Boolean {
        val words = normalize(heard).split(" ").filter { it.isNotEmpty() }
        if (words.size < MIN_WORDS) return false
        return !isEcho(heard, speaking)
    }

    /** Quanta parte delle parole sentite compare in quelle lette. */
    private fun overlap(heard: String, speaking: String): Double {
        val words = heard.split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty()) return 0.0
        val said = speaking.split(" ").filter { it.isNotEmpty() }.toSet()
        return words.count { it in said }.toDouble() / words.size
    }

    /** Minuscole, senza punteggiatura e senza spazi doppi: resta il parlato. */
    internal fun normalize(text: String): String = text.lowercase()
        .map { if (it.isLetterOrDigit() || it.isWhitespace()) it else ' ' }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim()
}
