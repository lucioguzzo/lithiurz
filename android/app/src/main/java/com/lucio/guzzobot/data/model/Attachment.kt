package com.lucio.guzzobot.data.model

import kotlinx.serialization.Serializable

enum class AttachmentKind {
    /** Immagine scelta dalla galleria o scattata ora. */
    IMAGE,

    /** Documento da cui estraiamo il testo (PDF, DOCX, testo, codice…). */
    DOCUMENT,

    /** Audio registrato o scelto dal telefono, da trascrivere. */
    AUDIO,

    /** Video: ne ricaviamo fotogrammi per il modello vision e l'audio da trascrivere. */
    VIDEO,

    /** Immagine prodotta dal modello. */
    GENERATED_IMAGE,

    /** Audio prodotto dal modello (lettura ad alta voce). */
    GENERATED_AUDIO,
}

@Serializable
data class Attachment(
    val id: String,
    val kind: AttachmentKind,
    val name: String,
    val mime: String,
    /** Percorso assoluto nella cartella privata dell'app. */
    val path: String,
    val size: Long = 0,
    /** Testo estratto dal documento o trascrizione dell'audio. */
    val text: String? = null,
    /** Descrizione o trascrizione prodotta da un modello vision. */
    val visionText: String? = null,
    /** Nota mostrata sotto l'allegato, es. il modello che l'ha trascritto. */
    val note: String? = null,
    /** Pagine di un PDF o fotogrammi di un video, come immagini. */
    val pageImages: List<String> = emptyList(),
    /** Traccia audio estratta da un video, da trascrivere. */
    val audioPath: String? = null,
) {
    val isImage: Boolean get() = kind == AttachmentKind.IMAGE || kind == AttachmentKind.GENERATED_IMAGE
    val isAudio: Boolean get() = kind == AttachmentKind.AUDIO || kind == AttachmentKind.GENERATED_AUDIO
    val isVideo: Boolean get() = kind == AttachmentKind.VIDEO
    val hasText: Boolean get() = !text.isNullOrBlank()
    val hasVisionText: Boolean get() = !visionText.isNullOrBlank()

    /** Immagini che un modello vision può guardare: l'immagine stessa o i fotogrammi. */
    val visualPaths: List<String>
        get() = when {
            pageImages.isNotEmpty() -> pageImages
            kind == AttachmentKind.IMAGE -> listOf(path)
            else -> emptyList()
        }
}

/** Puntatore a un modello di un provider configurato. */
@Serializable
data class ModelRef(
    val providerId: String,
    val model: String,
) {
    override fun toString(): String = model
}

/**
 * Quali modelli vedono le immagini. L'elenco è euristico, basato sui nomi usati
 * dai provider: se il tuo modello non viene riconosciuto puoi forzare l'invio
 * diretto dalle impostazioni.
 */
object ModelCapabilities {

    private val visionHints = listOf(
        "gpt-4o", "gpt-4.1", "gpt-4-turbo", "gpt-5", "o3", "o4-mini",
        "vision", "pixtral", "llava", "gemini", "claude", "qwen-vl", "qwen2-vl",
        "qwen2.5-vl", "qwen3-vl", "llama-3.2-11b", "llama-3.2-90b", "llama-4",
        "grok-2-vision", "grok-3", "grok-4", "internvl", "minicpm-v", "molmo",
        "phi-3.5-vision", "phi-4-multimodal", "mistral-small-3", "mistral-medium-3",
        "gemma-3", "step-1o", "glm-4v", "deepseek-vl", "moondream",
        // DeepSeek-V4.1-Flash vede le immagini; la variante "pro" no.
        "deepseek-flash", "deepseek-v4-flash-vision",
    )

    fun seesImages(model: String): Boolean =
        visionHints.any { model.contains(it, ignoreCase = true) }
}
