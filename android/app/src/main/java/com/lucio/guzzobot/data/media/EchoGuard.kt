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

    /** Parole che servono per interrompere a frase in corso. */
    private const val CUT_IN_WORDS = 3

    /** Sotto questa quota di parole in comune con la voce, parla una persona. */
    private const val CUT_IN_SIMILAR = 0.5

    /**
     * Vero se, mentre la voce legge, il parziale riconosciuto è una persona
     * che vuole la parola.
     *
     * Più severo di [shouldInterrupt]: si decide su un testo ancora a metà, e
     * sbagliare vuol dire zittirsi da soli. L'inizio di una voce, da solo, non
     * basta mai — la prima voce che il microfono sente è sempre quella
     * dell'altoparlante.
     */
    fun shouldCutIn(partial: String, speaking: String?): Boolean {
        val words = normalize(partial).split(" ").filter { it.isNotEmpty() }
        if (words.size < CUT_IN_WORDS) return false
        val voice = normalize(speaking.orEmpty())
        if (voice.isEmpty()) return true
        if (voice.contains(words.joinToString(" "))) return false
        return overlap(words.joinToString(" "), voice) < CUT_IN_SIMILAR
    }

    /**
     * Quello che l'utente ha detto, senza l'eco che lo precede.
     *
     * Il microfono era aperto mentre la voce leggeva: la frase riconosciuta
     * può cominciare con le ultime parole dell'app, prima di quelle della
     * persona che l'ha interrotta. Quelle in testa si tolgono; dal primo
     * termine estraneo in poi è tutto suo.
     */
    fun withoutEcho(heard: String, said: String?): String {
        val voice = normalize(said.orEmpty()).split(" ").filter { it.isNotEmpty() }.toSet()
        if (voice.isEmpty()) return heard.trim()
        val tokens = heard.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val first = tokens.indexOfFirst { token ->
            val word = normalize(token)
            word.isNotEmpty() && voice.none { same(word, it) }
        }
        return if (first < 0) "" else tokens.drop(first).joinToString(" ")
    }

    /** Quanta parte delle parole sentite compare in quelle lette. */
    private fun overlap(heard: String, speaking: String): Double {
        val words = heard.split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty()) return 0.0
        val said = speaking.split(" ").filter { it.isNotEmpty() }.toSet()
        return words.count { word -> said.any { same(word, it) } }.toDouble() / words.size
    }

    /**
     * La stessa parola, anche storpiata: il riconoscimento sbaglia spesso le
     * desinenze dell'eco («capitali» per «capitale»). Sbagliare qui vuol dire
     * non interrompere, che è il guaio minore: un tocco zittisce comunque.
     */
    private fun same(a: String, b: String): Boolean =
        a == b || (a.length >= 5 && b.length >= 5 && a.take(4) == b.take(4))

    /** Minuscole, senza punteggiatura e senza spazi doppi: resta il parlato. */
    internal fun normalize(text: String): String = text.lowercase()
        .map { if (it.isLetterOrDigit() || it.isWhitespace()) it else ' ' }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim()
}
