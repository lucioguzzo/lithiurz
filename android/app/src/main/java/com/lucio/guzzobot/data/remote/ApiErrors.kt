package com.lucio.guzzobot.data.remote

/**
 * Riconoscere *perché* un provider ha detto di no.
 *
 * I messaggi d'errore sono testo libero e cambiano da provider a provider, ma
 * alcuni rifiuti vogliono una reazione precisa invece che un messaggio rosso.
 * Qui stanno quei riconoscimenti, in logica pura: indovinare su una stringa è
 * fragile, e almeno è verificabile.
 */
object ApiErrors {

    private val toolsMarkers = listOf(
        "function calling is not enabled",
        "function calling is not supported",
        "does not support tools",
        "tools are not supported",
        "tool use is not supported",
        "unsupported parameter: 'tools'",
        "unsupported value: 'tools'",
        "tool_choice",
        "function_call is not supported",
    )

    /**
     * Vero se il provider ha rifiutato la richiesta perché *quel modello* non
     * sa usare strumenti. Non è un guasto: è un limite del modello, e la cosa
     * sensata è riprovare senza strumenti invece di non rispondere affatto.
     */
    fun toolsUnsupported(message: String?): Boolean {
        val text = message?.lowercase() ?: return false
        if (!text.contains("tool") && !text.contains("function")) return false
        return toolsMarkers.any { text.contains(it) }
    }
}
