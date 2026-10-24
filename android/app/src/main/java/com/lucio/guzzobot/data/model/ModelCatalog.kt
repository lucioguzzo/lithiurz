package com.lucio.guzzobot.data.model

/**
 * I provider cambiano i modelli senza avvisare: nomi che spariscono, versioni
 * nuove, sigle diverse. L'elenco scritto nell'app è solo un punto di partenza;
 * la verità è l'endpoint /models del provider, e questo oggetto dice come
 * riconciliare le due cose.
 */
object ModelCatalog {

    /** Oltre questa soglia l'elenco del provider è un catalogo, non una scelta. */
    const val CURATED_LIMIT = 60

    /**
     * Se il provider espone pochi modelli, il suo elenco sostituisce il nostro.
     * Se ne espone centinaia (OpenRouter), si tiene la selezione curata
     * togliendo però quelli che non esistono più.
     */
    fun merge(curated: List<String>, fetched: List<String>, limit: Int = CURATED_LIMIT): List<String> {
        if (fetched.isEmpty()) return curated
        if (fetched.size <= limit) return fetched.distinct()
        val alive = curated.filter { it in fetched }
        return alive.ifEmpty { fetched.take(limit) }
    }

    /**
     * I modelli da mostrare nel selettore della chat.
     *
     * Un catalogo come quello di Gemini è pieno di modelli che non reggono un
     * turno di conversazione: toccarli darebbe un errore dell'API. Vengono
     * però tenuti nell'elenco del provider, perché sono esattamente quelli che
     * servono ai ruoli multimodali — generare immagini, trascrivere, leggere
     * ad alta voce. Si nascondono qui, non si cancellano là.
     */
    fun chatModels(models: List<String>): List<String> =
        models.filterNot { isAccessory(it) }.ifEmpty { models }

    private val accessoryMarkers = listOf(
        "embedding", "embed-", "-tts", "tts-", "-live", "transcribe", "whisper",
        "-image", "image-", "dall-e", "moderation", "rerank", "guard", "-asr",
    )

    /** I modelli di un elenco capaci di generare immagini. */
    fun imageModels(models: List<String>): List<String> =
        models.filter { model ->
            listOf("image", "dall-e", "imagen", "flux", "sdxl", "diffusion")
                .any { model.contains(it, ignoreCase = true) }
        }

    /** I modelli di un elenco capaci di trascrivere l'audio. */
    fun transcribeModels(models: List<String>): List<String> =
        models.filter { model ->
            listOf("whisper", "transcribe").any { model.contains(it, ignoreCase = true) }
        }

    /** Vero se il modello serve a tutt'altro che conversare. */
    fun isAccessory(model: String): Boolean =
        accessoryMarkers.any { model.contains(it, ignoreCase = true) }

    /**
     * Il modello scelto non c'è più: si cerca il parente più prossimo, cioè
     * quello che condivide il prefisso più lungo (glm-5.4 → glm-5.3).
     */
    fun replacementFor(missing: String, available: List<String>): String? {
        if (missing.isBlank() || available.isEmpty()) return null
        if (missing in available) return missing
        val family = missing.takeWhile { it.isLetter() || it == '-' || it == '/' || it == '.' }
            .trimEnd('-', '.')
        val candidates = available.filter { it.startsWith(family, ignoreCase = true) }
            .ifEmpty {
                available.filter { shared(it, missing) >= 4 }
            }
        return candidates.maxByOrNull { shared(it, missing) }
    }

    private fun shared(a: String, b: String): Int {
        var index = 0
        while (index < minOf(a.length, b.length) &&
            a[index].lowercaseChar() == b[index].lowercaseChar()
        ) {
            index++
        }
        return index
    }
}
