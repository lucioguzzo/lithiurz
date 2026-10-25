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

    private val quotaMarkers = listOf(
        "exceeded your current quota",
        "quota exceeded",
        "billing",
        "insufficient_quota",
        "credit",
        "plan and billing",
    )

    /**
     * Vero se vale la pena riprovare fra poco.
     *
     * «This model is currently experiencing high demand» non è un guasto: è
     * una coda, e il provider stesso dice di riprovare. Un 429 invece va
     * distinto: se è la quota del giorno a essere finita, riprovare fra due
     * secondi non cambia niente e fa solo perdere tempo.
     */
    fun transient(code: Int, message: String?): Boolean {
        val text = message?.lowercase().orEmpty()
        if (quotaMarkers.any { text.contains(it) }) return false
        return when (code) {
            500, 502, 503, 504 -> true
            429 -> true
            else -> false
        }
    }

    /** Il messaggio da mostrare quando anche i ritentativi non sono bastati. */
    fun exhausted(code: Int): String = when (code) {
        429 -> "Il provider sta limitando le richieste: riprova fra qualche minuto"
        else -> "Il servizio è sovraccarico in questo momento: riprova fra poco"
    }

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
