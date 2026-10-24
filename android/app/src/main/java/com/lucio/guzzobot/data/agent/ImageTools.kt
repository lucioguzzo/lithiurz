package com.lucio.guzzobot.data.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * La generazione di immagini come strumento del modello.
 *
 * Finora era un pulsante: l'utente scriveva il prompt e premeva. Qui diventa
 * qualcosa che il modello può decidere di usare da sé, nel mezzo di un lavoro
 * — scrivere l'articolo e produrne la copertina senza che nessuno cambi
 * schermata.
 */
object ImageTools {

    const val CREATE = "image_create"

    /** Chi sa davvero produrre l'immagine; lo implementa chi ha i provider in mano. */
    fun interface Maker {
        /** Restituisce una riga che descrive dove è finita l'immagine. */
        suspend fun create(prompt: String): String
    }

    fun definition(): JsonObject = buildJsonObject {
        put("type", "function")
        put(
            "function",
            buildJsonObject {
                put("name", CREATE)
                put(
                    "description",
                    "Crea un'immagine da una descrizione testuale e la mostra " +
                        "all'utente. Usalo quando serve un'illustrazione, uno " +
                        "schema o una copertina. Non serve per leggere o " +
                        "descrivere immagini che l'utente ha già allegato.",
                )
                put(
                    "parameters",
                    buildJsonObject {
                        put("type", "object")
                        put(
                            "properties",
                            buildJsonObject {
                                put(
                                    "prompt",
                                    buildJsonObject {
                                        put("type", "string")
                                        put(
                                            "description",
                                            "Descrizione dell'immagine, in inglese se " +
                                                "il modello di immagini lo preferisce: " +
                                                "soggetto, stile, inquadratura, luce.",
                                        )
                                    },
                                )
                            },
                        )
                        put("required", buildJsonArray { add(JsonPrimitive("prompt")) })
                    },
                )
            },
        )
    }

    fun describe(prompt: String): String = "Creo un'immagine: " + prompt.take(80)
}
