package com.lucio.guzzobot.data.media

import android.speech.tts.Voice

/**
 * Una voce installata sul telefono, descritta per come si sceglie.
 *
 * Il motore la chiama «it-it-x-kda-local», che non dice niente a nessuno.
 * Quello che serve a decidere è: che lingua parla, quanto suona bene, e se
 * funziona senza rete.
 */
data class VoiceOption(
    /** Il nome tecnico, quello che va salvato e ridato al motore. */
    val id: String,
    val locale: String,
    val quality: Int,
    val offline: Boolean,
) {
    /** La riga da leggere nell'elenco. */
    val label: String
        get() = buildString {
            append(locale)
            append(" · ")
            append(qualityLabel(quality))
            if (offline) append(" · offline")
        }

    companion object {
        /**
         * I livelli del motore in parole.
         *
         * Android li espone come numeri da 100 a 500; le voci neurali stanno
         * in alto, quelle meccaniche di serie in basso — ed è esattamente la
         * differenza che si sente.
         */
        fun qualityLabel(quality: Int): String = when {
            quality >= Voice.QUALITY_VERY_HIGH -> "qualità ottima"
            quality >= Voice.QUALITY_HIGH -> "qualità alta"
            quality >= Voice.QUALITY_NORMAL -> "qualità normale"
            quality >= Voice.QUALITY_LOW -> "qualità bassa"
            else -> "qualità minima"
        }
    }
}

/** A che punto è la conversazione parlata. */
enum class VoiceStage {
    /** Spenta. */
    OFF,

    /** Microfono aperto, aspetta che l'utente parli. */
    LISTENING,

    /** Ha sentito, sta aspettando la risposta del modello. */
    THINKING,

    /** Sta leggendo la risposta — e resta in ascolto, per poter essere interrotta. */
    SPEAKING,
}
