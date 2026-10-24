package com.lucio.guzzobot.data.model

/**
 * Nessuno trascrive a mano quaranta caratteri senza sbagliare.
 *
 * Chi crea una chiave la copia: quando l'utente torna nell'app, se gli appunti
 * contengono qualcosa che ha la forma di una chiave, si propone quella invece
 * di chiedere di incollare. Il riconoscimento è volutamente stretto — meglio
 * non proporre niente che proporre una frase qualunque presa dagli appunti.
 */
object KeyDetector {

    /** La chiave di questo provider trovata nel testo, se c'è. */
    fun forProvider(text: String?, provider: Provider): String? {
        val pattern = provider.keyPattern ?: return null
        val content = text?.takeIf { it.isNotBlank() } ?: return null
        return Regex(pattern).find(content)?.value
    }

    /**
     * I provider a cui una chiave incollata può appartenere. Più di uno quando
     * le forme si somigliano (OpenAI e DeepSeek usano entrambi `sk-`): in quel
     * caso tocca all'utente dire quale, non all'app indovinare.
     */
    fun candidates(text: String?, providers: List<Provider>): List<Provider> {
        val content = text?.takeIf { it.isNotBlank() } ?: return emptyList()
        val matches = providers.filter { forProvider(content, it) != null }
        if (matches.isEmpty()) return emptyList()
        // Vince il pattern col prefisso fisso più lungo, non la corrispondenza
        // più lunga: "sk-or-v1-" batte "sk-", che invece coprirebbe la stessa
        // stringa per intero.
        val best = matches.maxOf { literalPrefix(it.keyPattern.orEmpty()).length }
        return matches.filter { literalPrefix(it.keyPattern.orEmpty()).length == best }
    }

    /** La parte iniziale del pattern che è testo e non espressione regolare. */
    private fun literalPrefix(pattern: String): String =
        pattern.takeWhile { it !in "[](){}.*+?^$|\\" }

    /** Vero se il testo contiene qualcosa che somiglia a una chiave API. */
    fun looksLikeKey(text: String?): Boolean =
        candidates(text, Providers.defaults).isNotEmpty()
}
