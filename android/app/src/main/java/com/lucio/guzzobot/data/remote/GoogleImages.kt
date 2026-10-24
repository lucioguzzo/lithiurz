package com.lucio.guzzobot.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * La modifica di un'immagine esistente.
 *
 * Generare un'immagine da zero passa da `/images/generations`, l'endpoint
 * compatibile con l'API OpenAI che l'app già usa. Modificarne una no: quella
 * strada accetta solo testo. Serve l'API nativa di Google, che prende il
 * prompt e l'immagine insieme.
 *
 * Qui dentro sta solo la logica pura — comporre le richieste e ritrovare
 * l'immagine nella risposta — così si può verificare senza rete.
 */
object GoogleImages {

    /**
     * Dalla base dell'endpoint compatibile si ricava quella nativa:
     * `…/v1beta/openai/` diventa `…/v1beta`.
     */
    fun nativeBase(baseUrl: String): String =
        baseUrl.trim().trimEnd('/').removeSuffix("/openai")

    /** La forma corrente: un'unica lista di parti, testo e immagini insieme. */
    fun interactionsBody(
        model: String,
        prompt: String,
        images: List<Picture>,
        mimeType: String?,
    ): JsonObject = buildJsonObject {
        put("model", model)
        put(
            "input",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", prompt)
                    },
                )
                images.forEach { picture ->
                    add(
                        buildJsonObject {
                            put("type", "image")
                            put("mime_type", picture.mimeType)
                            put("data", picture.base64)
                        },
                    )
                }
            },
        )
        if (mimeType != null) {
            put(
                "response_format",
                buildJsonObject {
                    put("type", "image")
                    put("mime_type", mimeType)
                },
            )
        }
    }

    /**
     * La forma precedente, tenuta come ripiego: le due convivono e un
     * endpoint che risponde «non esiste» non deve far fallire la modifica.
     */
    fun generateContentBody(prompt: String, images: List<Picture>): JsonObject = buildJsonObject {
        put(
            "contents",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put(
                            "parts",
                            buildJsonArray {
                                add(buildJsonObject { put("text", prompt) })
                                images.forEach { picture ->
                                    add(
                                        buildJsonObject {
                                            put(
                                                "inline_data",
                                                buildJsonObject {
                                                    put("mime_type", picture.mimeType)
                                                    put("data", picture.base64)
                                                },
                                            )
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            },
        )
    }

    /**
     * Ritrova le immagini nella risposta.
     *
     * Le due API annidano il risultato in posti diversi — `output_image`,
     * `steps[].content[]`, `candidates[].content.parts[].inline_data` — e la
     * documentazione non fissa l'involucro. Invece di scommettere su un
     * percorso si attraversa l'albero e si raccoglie ogni blocco che dichiara
     * di essere un'immagine e porta dei byte: se Google sposta il campo, questo
     * continua a funzionare.
     */
    fun extractImages(element: JsonElement): List<String> {
        val found = mutableListOf<String>()
        walk(element, found)
        return found.distinct()
    }

    private fun walk(element: JsonElement, found: MutableList<String>) {
        when (element) {
            is JsonObject -> {
                imageData(element)?.let(found::add)
                element.values.forEach { walk(it, found) }
            }

            is JsonArray -> element.forEach { walk(it, found) }
            else -> Unit
        }
    }

    /** I byte, se questo oggetto è davvero una parte-immagine. */
    private fun imageData(node: JsonObject): String? {
        val data = (node["data"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return null
        if (data.length < MIN_BASE64) return null
        val type = (node["type"] as? JsonPrimitive)?.content.orEmpty()
        val mime = listOf("mime_type", "mimeType")
            .firstNotNullOfOrNull { (node[it] as? JsonPrimitive)?.content }
            .orEmpty()
        // Dichiarato immagine, o con un tipo MIME che lo è. Un blocco di testo
        // con un campo "data" non deve passare per immagine.
        return if (type == "image" || mime.startsWith("image/")) data else null
    }

    /** Un'immagine in ingresso, già in base64. */
    data class Picture(val base64: String, val mimeType: String = "image/jpeg")

    private const val MIN_BASE64 = 64
}
