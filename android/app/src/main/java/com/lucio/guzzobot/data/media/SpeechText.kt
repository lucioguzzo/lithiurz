package com.lucio.guzzobot.data.media

/**
 * Preparare il testo per essere letto, non guardato.
 *
 * Una risposta è scritta in markdown e piena di cose che a voce sono rumore:
 * gli asterischi del grassetto, i cancelletti dei titoli, gli indirizzi web,
 * e soprattutto i blocchi di codice — che letti ad alta voce sono una tortura
 * e durano minuti.
 *
 * E va letta mentre arriva, non dopo: per questo serve sapere dove finisce una
 * frase, così la si può mandare al sintetizzatore mentre il resto si scrive.
 */
object SpeechText {

    /** Dove il testo diventa parlato: via la grafica, restano le parole. */
    fun clean(markdown: String): String {
        var text = markdown

        // I blocchi di codice non si leggono: si annunciano.
        text = Regex("```[a-zA-Z0-9+#.-]*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
            .replace(text) { " (blocco di codice) " }
        text = Regex("```.*?```", RegexOption.DOT_MATCHES_ALL).replace(text) { " (blocco di codice) " }

        // Un link si legge per quello che dice, non per il suo indirizzo.
        text = Regex("""\[([^\]]+)]\((?:[^)]*)\)""").replace(text) { it.groupValues[1] }
        text = Regex("""https?://\S+""").replace(text, " (link) ")

        // Il codice in linea resta, senza gli apici che lo marcano.
        text = text.replace(Regex("`([^`]*)`"), "$1")

        // Enfasi, titoli, elenchi, citazioni: segni per gli occhi.
        text = text.replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
        text = text.replace(Regex("""\*([^*]+)\*"""), "$1")
        text = text.replace(Regex("""(?m)^\s{0,3}#{1,6}\s+"""), "")
        text = text.replace(Regex("""(?m)^\s{0,3}[-*+]\s+"""), "")
        text = text.replace(Regex("""(?m)^\s{0,3}>\s?"""), "")
        text = text.replace(Regex("""(?m)^\s*\|.*\|\s*$"""), " (tabella) ")
        text = text.replace(Regex("""(?m)^\s*[-|: ]{4,}\s*$"""), "")

        // Spazi e righe vuote accumulate dai tagli qui sopra.
        return text.replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{2,}"), "\n")
            .trim()
    }

    /**
     * Le frasi complete già pronte, e quello che resta in attesa del seguito.
     *
     * Mentre la risposta arriva a pezzi, si legge solo ciò che è finito: una
     * frase troncata a metà, letta, suonerebbe sbagliata. Con [flush] si
     * prende anche l'ultimo pezzo, perché a risposta finita non arriva altro.
     */
    fun take(buffer: String, flush: Boolean = false): Pair<List<String>, String> {
        val sentences = mutableListOf<String>()
        var start = 0
        var index = 0
        while (index < buffer.length) {
            val char = buffer[index]
            val boundary = when {
                char == '\n' -> true
                char == '.' || char == '!' || char == '?' || char == '…' -> isEnd(buffer, index)
                else -> false
            }
            if (boundary) {
                val piece = buffer.substring(start, index + 1).trim()
                if (piece.isNotEmpty()) sentences += piece
                start = index + 1
            }
            index++
        }
        val rest = buffer.substring(start)
        if (flush) {
            val last = rest.trim()
            if (last.isNotEmpty()) sentences += last
            return sentences to ""
        }
        return sentences to rest
    }

    /**
     * Un punto chiude una frase solo se dopo c'è uno spazio o la fine, e se
     * prima non c'è una cifra: «3.5» e «1.000» non sono due frasi.
     */
    private fun isEnd(buffer: String, index: Int): Boolean {
        val before = buffer.getOrNull(index - 1)
        val after = buffer.getOrNull(index + 1)
        if (buffer[index] == '.' && before?.isDigit() == true && after?.isDigit() == true) return false
        return after == null || after == ' ' || after == '\n'
    }
}
