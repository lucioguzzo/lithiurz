package com.lucio.guzzobot.data.remote

import java.net.URLEncoder

/**
 * L'unico generatore di immagini davvero senza chiave.
 *
 * Non è un endpoint compatibile con l'API OpenAI: è una GET il cui percorso
 * *è* il prompt, e che restituisce direttamente i byte dell'immagine. Da qui
 * la forma strana di questo oggetto rispetto agli altri provider.
 *
 * Quello che dà in cambio di niente va detto per intero: una richiesta alla
 * volta per indirizzo IP e almeno 45 secondi fra una e l'altra, massimo
 * 768×768, il logo del servizio stampato sopra, e un URL che è pubblico —
 * il risultato viene messo in cache e chiunque abbia quell'indirizzo rivede
 * la stessa immagine.
 */
object Pollinations {

    const val BASE_URL = "https://image.pollinations.ai"

    /** Oltre quest'area il servizio non va: le proporzioni vengono conservate. */
    const val MAX_SIDE = 768

    /**
     * L'indirizzo da cui scaricare l'immagine.
     *
     * Il prompt sta nel percorso, quindi va codificato: una barra o un punto
     * interrogativo lasciati passare cambierebbero la richiesta.
     */
    fun url(
        prompt: String,
        size: String? = null,
        seed: Int? = null,
        safe: Boolean = false,
        baseUrl: String = BASE_URL,
    ): String {
        val (width, height) = dimensions(size)
        val encoded = URLEncoder.encode(prompt.trim(), "UTF-8").replace("+", "%20")
        val query = buildList {
            add("width=$width")
            add("height=$height")
            seed?.let { add("seed=$it") }
            if (safe) add("safe=true")
        }.joinToString("&")
        return baseUrl.trimEnd('/') + "/prompt/" + encoded + "?" + query
    }

    /**
     * Da «1024x1024» ai lati ammessi, conservando le proporzioni: chiedere di
     * più non fa errore, fa solo tornare qualcosa di diverso da quello che si
     * è chiesto.
     */
    fun dimensions(size: String?): Pair<Int, Int> {
        val parts = size?.lowercase()?.split("x")?.mapNotNull { it.trim().toIntOrNull() }
        val width = parts?.getOrNull(0)?.takeIf { it > 0 } ?: MAX_SIDE
        val height = parts?.getOrNull(1)?.takeIf { it > 0 } ?: width
        val longest = maxOf(width, height)
        if (longest <= MAX_SIDE) return width to height
        val factor = MAX_SIDE.toDouble() / longest
        return maxOf(1, (width * factor).toInt()) to maxOf(1, (height * factor).toInt())
    }

    /** Il messaggio da dare quando il servizio dice di aspettare. */
    fun waitMessage(retryAfter: String?): String {
        val seconds = retryAfter?.trim()?.toIntOrNull()
        return if (seconds != null && seconds > 0) {
            "Pollinations accetta una richiesta alla volta: riprova fra $seconds secondi"
        } else {
            "Pollinations accetta una richiesta alla volta, almeno 45 secondi fra una e l'altra"
        }
    }
}
