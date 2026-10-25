package com.lucio.guzzobot.data.skills

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Le competenze che il modello carica da sé.
 *
 * Fin qui una competenza la sceglieva l'utente dal menù. Qui diventa qualcosa
 * che il modello può andarsi a prendere quando riconosce il lavoro: gli
 * strumenti dichiarano *quali* competenze esistono e a cosa servono — poche
 * righe ciascuna — e solo quando ne carica una riceve le istruzioni per
 * esteso. È lo stesso motivo per cui un manuale ha un indice: mettere tutto
 * nel prompt costerebbe a ogni messaggio, anche quando non serve niente.
 */
object SkillTools {

    const val LOAD = "skill_load"

    /** Chi applica davvero la competenza alla conversazione. */
    fun interface Loader {
        /** Restituisce le istruzioni, o null se quel nome non esiste. */
        suspend fun load(id: String): String?
    }

    /**
     * Lo strumento esiste solo se c'è qualcosa da caricare, e l'elenco delle
     * competenze sta nella sua descrizione: così il modello sa cosa può
     * chiedere senza che il prompt di sistema cresca.
     */
    fun definition(skills: List<Skill>): JsonObject? {
        val usable = SkillCatalog.active(skills)
        if (usable.isEmpty()) return null
        return buildJsonObject {
            put("type", "function")
            put(
                "function",
                buildJsonObject {
                    put("name", LOAD)
                    put("description", describe(usable))
                    put(
                        "parameters",
                        buildJsonObject {
                            put("type", "object")
                            put(
                                "properties",
                                buildJsonObject {
                                    put(
                                        "name",
                                        buildJsonObject {
                                            put("type", "string")
                                            put(
                                                "description",
                                                "Il nome della competenza da caricare, " +
                                                    "esattamente come compare nell'elenco.",
                                            )
                                            put(
                                                "enum",
                                                buildJsonArray {
                                                    usable.forEach { add(JsonPrimitive(it.id)) }
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                            put("required", buildJsonArray { add(JsonPrimitive("name")) })
                        },
                    )
                },
            )
        }
    }

    /** L'indice: nome, a cosa serve, e la regola per non abusarne. */
    internal fun describe(skills: List<Skill>): String = buildString {
        append(
            "Carica il modo di lavorare adatto al compito, PRIMA di cominciare, " +
                "se uno di questi lo riguarda. Ricevuto il testo, seguilo. " +
                "Caricane al massimo una per messaggio, e nessuna se il compito " +
                "non rientra in questi casi. Disponibili: ",
        )
        append(
            skills.joinToString("; ") { skill ->
                "${skill.id} (${skill.name}: ${skill.description})"
            },
        )
        append(".")
    }

    /** Vero se il modello ha chiesto una competenza che non esiste. */
    fun unknown(id: String, skills: List<Skill>): Boolean =
        SkillCatalog.find(skills, id) == null

    fun describe(id: String, skills: List<Skill>): String =
        SkillCatalog.find(skills, id)?.let { "Carico la competenza: ${it.name}" }
            ?: "Competenza sconosciuta: $id"
}
