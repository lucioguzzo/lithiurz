package com.lucio.guzzobot.data.model

import kotlinx.serialization.Serializable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Un endpoint compatibile con l'API OpenAI (/chat/completions).
 * Copre DeepSeek, OpenRouter, Groq, Mistral, xAI, Together, Ollama, LM Studio…
 */
@Serializable
data class Provider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val models: List<String> = emptyList(),
    val keyUrl: String? = null,
    val builtIn: Boolean = false,
    /** Come si chiedono le immagini a questo provider. */
    val imageApi: ImageApi = ImageApi.OPENAI,
    /** Un provider che fa solo immagini non va mostrato fra i modelli di chat. */
    val imageOnly: Boolean = false,
    /**
     * Vero se generare immagini richiede la fatturazione attiva, anche quando
     * la chat è gratuita. Google è così: nessun suo modello di immagini sta
     * nel piano gratuito. Serve a non sceglierli da soli, lasciando l'utente
     * davanti a un errore che sembra un guasto.
     */
    val imagesNeedPayment: Boolean = false,
    /** Se il provider ha un piano gratuito vero, cosa offre e cosa chiede. */
    val freeTier: FreeTier? = null,
    /**
     * Come si riconosce una chiave di questo provider. Serve a non far
     * trascrivere a mano una stringa di quaranta caratteri: se è negli
     * appunti, l'app se ne accorge e la propone.
     */
    val keyPattern: String? = null,
) {
    /**
     * Non tutti chiedono una credenziale: Ollama gira in locale, Pollinations
     * non ne ha proprio. Senza questa distinzione l'app li considererebbe non
     * configurati per sempre.
     */
    val requiresKey: Boolean
        get() = imageApi != ImageApi.POLLINATIONS &&
            baseUrl.startsWith("https://", ignoreCase = true)
}

/** Le forme di richiesta per generare immagini che l'app sa parlare. */
@Serializable
enum class ImageApi {
    /** POST su /images/generations, come OpenAI. */
    OPENAI,

    /** Una GET il cui percorso è il prompt: Pollinations, senza chiave. */
    POLLINATIONS,
}

/**
 * Un piano gratuito, descritto per quello che è.
 *
 * Nessuno di questi endpoint funziona senza credenziale: nel cloud chi regala
 * calcolo a chiunque senza identificare nessuno viene svuotato in ore. Quello
 * che esiste è il gratuito *senza pagare* — iscrizione gratis, nessuna carta —
 * e questa classe serve a dirlo senza promettere altro.
 */
@Serializable
data class FreeTier(
    /** La pagina da cui si ottiene la chiave, gratis. */
    val signupUrl: String,
    /** I limiti dichiarati dal provider, per non farli scoprire a un 429. */
    val limits: String = "",
    /** Se solo una parte del catalogo è gratis, come si riconosce. */
    val freeMarker: String? = null,
    /**
     * I modelli gratuiti, quando sono un elenco preciso e non una famiglia.
     *
     * Serve dove il nome inganna: da Z.ai «flash» non vuol dire gratuito —
     * GLM-5.3-Flash si paga, GLM-4.7-Flash no — e un marcatore prometterebbe
     * gratis quello che gratis non è.
     */
    val freeModels: List<String> = emptyList(),
    val notes: List<FreeNote> = emptyList(),
) {
    /** Un modello è gratuito se è nell'elenco, se porta il marcatore, o se lo è tutto. */
    fun covers(model: String): Boolean = when {
        freeModels.isNotEmpty() -> freeModels.any { model.contains(it, ignoreCase = true) }
        freeMarker != null -> model.contains(freeMarker, ignoreCase = true)
        else -> true
    }
}

/** Quello che un piano gratuito dà in più, o si prende in cambio. */
@Serializable
enum class FreeNote(val warning: Boolean) {
    /** Genera immagini. */
    CREATES_IMAGES(false),
    /** Non serve nessuna chiave, davvero. */
    NO_KEY(false),
    /** Il risultato sta a un indirizzo pubblico, e chi ce l'ha lo rivede. */
    PUBLIC_RESULTS(true),
    /** Le immagini escono col logo del servizio. */
    WATERMARK(true),
    /** Vede le immagini: fra i gratuiti è la rarità, non la norma. */
    SEES_IMAGES(false),
    /** Trascrive l'audio con Whisper. */
    TRANSCRIBES(false),
    /** Nessuna carta di credito, mai. */
    NO_CARD(false),
    /** I prompt del piano gratuito finiscono nell'addestramento. */
    TRAINS_ON_DATA(true),
    /** Non serve una chiave, serve una macchina accesa in rete locale. */
    NEEDS_LOCAL_MACHINE(true),
    /** Solo una parte del catalogo è gratis. */
    PARTIAL_CATALOG(true),
    /** La chat è gratis, le immagini no. */
    IMAGES_PAID(true),
}

object Providers {
    val defaults: List<Provider> = listOf(
        Provider(
            id = "deepseek",
            keyPattern = "sk-[A-Za-z0-9]{20,}",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            // Nomi aggiornati: deepseek-chat e deepseek-reasoner sono stati ritirati.
            models = listOf("deepseek-flash", "deepseek-v4-pro"),
            keyUrl = "https://platform.deepseek.com/api_keys",
            builtIn = true,
        ),
        Provider(
            id = "openrouter",
            keyPattern = "sk-or-v1-[A-Za-z0-9]{32,}",
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            models = listOf(
                "deepseek/deepseek-chat",
                "qwen/qwen-2.5-72b-instruct",
                "meta-llama/llama-3.3-70b-instruct",
                "qwen/qwen2.5-vl-72b-instruct",
                "mistralai/pixtral-large-2411",
            ),
            keyUrl = "https://openrouter.ai/keys",
            builtIn = true,
            freeTier = FreeTier(
                signupUrl = "https://openrouter.ai/keys",
                limits = "20 richieste al minuto, 50 al giorno",
                freeMarker = ":free",
                notes = listOf(FreeNote.NO_CARD, FreeNote.PARTIAL_CATALOG),
            ),
        ),
        Provider(
            id = "groq",
            keyPattern = "gsk_[A-Za-z0-9]{40,}",
            name = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            models = listOf(
                "llama-3.3-70b-versatile",
                "llama-3.1-8b-instant",
                "meta-llama/llama-4-scout-17b-16e-instruct",
                "whisper-large-v3-turbo",
            ),
            keyUrl = "https://console.groq.com/keys",
            builtIn = true,
            freeTier = FreeTier(
                signupUrl = "https://console.groq.com/keys",
                // I limiti del piano gratuito Groq non li pubblica: stanno
                // nella console dell'account. Meglio dirlo che inventarli.
                limits = "limiti per modello, visibili nella console di Groq",
                notes = listOf(FreeNote.NO_CARD, FreeNote.TRANSCRIBES),
            ),
        ),
        Provider(
            id = "mistral",
            name = "Mistral",
            baseUrl = "https://api.mistral.ai/v1",
            models = listOf("mistral-large-latest", "mistral-small-latest"),
            keyUrl = "https://console.mistral.ai/api-keys",
            builtIn = true,
            freeTier = FreeTier(
                signupUrl = "https://console.mistral.ai/api-keys",
                limits = "limiti del piano «Free», visibili nel pannello Mistral",
                notes = listOf(FreeNote.NO_CARD),
            ),
        ),
        Provider(
            id = "xai",
            keyPattern = "xai-[A-Za-z0-9]{40,}",
            name = "xAI",
            baseUrl = "https://api.x.ai/v1",
            models = listOf("grok-2-latest", "grok-2-image"),
            keyUrl = "https://console.x.ai",
            builtIn = true,
        ),
        Provider(
            id = "zai",
            name = "Z.ai (GLM)",
            baseUrl = "https://api.z.ai/api/paas/v4",
            // Punto di partenza: l'elenco vero arriva dall'endpoint /models.
            // I primi tre sono quelli dichiarati gratuiti dal listino Z.ai.
            models = listOf(
                "glm-4.7-flash",
                "glm-4.5-flash",
                "glm-4.6v-flash",
                "glm-5.4",
                "glm-5.1",
            ),
            keyUrl = "https://z.ai/manage-apikey/apikey-list",
            builtIn = true,
            freeTier = FreeTier(
                signupUrl = "https://z.ai/manage-apikey/apikey-list",
                limits = "senza costo a token; limiti non dichiarati dal provider",
                freeModels = listOf("glm-4.7-flash", "glm-4.5-flash", "glm-4.6v-flash"),
                notes = listOf(FreeNote.NO_CARD, FreeNote.PARTIAL_CATALOG),
            ),
        ),
        Provider(
            id = "moonshot",
            keyPattern = "sk-[A-Za-z0-9]{32,}",
            name = "Moonshot (Kimi)",
            baseUrl = "https://api.moonshot.ai/v1",
            models = listOf("kimi-k3.1", "kimi-k3", "kimi-k2.6"),
            keyUrl = "https://platform.moonshot.ai/console/api-keys",
            builtIn = true,
        ),
        Provider(
            id = "google",
            keyPattern = "AIza[0-9A-Za-z_\\-]{35}",
            name = "Google AI Studio (Gemini)",
            // L'endpoint compatibile con l'API OpenAI, documentato da Google:
            // regge /chat/completions in streaming, /models e le immagini.
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
            // Punto di partenza: l'elenco vero arriva da /models.
            models = listOf(
                "gemini-3.8-flash",
                "gemini-3.6-flash",
                "gemini-3.5-flash-lite",
                // Generano immagini sullo stesso endpoint compatibile con
                // l'API OpenAI, ma NON sono nel piano gratuito: servono a chi
                // ha attivato la fatturazione. Il vecchio gemini-2.5-flash-image
                // non c'è più, dismesso il 2 ottobre 2026.
                "gemini-3.1-flash-image",
                "gemini-3-pro-image",
            ),
            keyUrl = "https://aistudio.google.com/apikey",
            builtIn = true,
            imagesNeedPayment = true,
            freeTier = FreeTier(
                signupUrl = "https://aistudio.google.com/apikey",
                limits = "limiti per progetto, visibili su aistudio.google.com/rate-limit",
                notes = listOf(
                    FreeNote.NO_CARD,
                    FreeNote.SEES_IMAGES,
                    FreeNote.IMAGES_PAID,
                    FreeNote.TRAINS_ON_DATA,
                ),
            ),
        ),
        Provider(
            id = "cerebras",
            keyPattern = "csk-[A-Za-z0-9]{20,}",
            name = "Cerebras",
            baseUrl = "https://api.cerebras.ai/v1",
            models = listOf("gpt-oss-120b", "qwen-3.8-27b"),
            keyUrl = "https://cloud.cerebras.ai",
            builtIn = true,
            // Niente piano gratuito: i documenti di Cerebras dicono che non
            // esiste più una quota a costo zero rinnovabile, e che i 5 dollari
            // di prova arrivano solo dopo aver inserito un metodo di pagamento.
            // In una sezione che promette «nessuna carta» non può starci.
        ),
        Provider(
            id = "openai",
            keyPattern = "sk-[A-Za-z0-9_\\-]{20,}",
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            models = listOf("gpt-4o", "gpt-4o-mini", "whisper-1", "gpt-image-1", "tts-1"),
            keyUrl = "https://platform.openai.com/api-keys",
            builtIn = true,
        ),
        Provider(
            id = "pollinations",
            name = "Pollinations (immagini)",
            baseUrl = "https://image.pollinations.ai",
            // Il servizio non documenta un parametro per scegliere il modello:
            // questo nome serve solo a poterlo selezionare come ruolo.
            models = listOf("pollinations"),
            builtIn = true,
            imageApi = ImageApi.POLLINATIONS,
            imageOnly = true,
            freeTier = FreeTier(
                signupUrl = "https://pollinations.ai",
                limits = "una richiesta alla volta, almeno 45 secondi fra una e " +
                    "l'altra; massimo 768×768",
                notes = listOf(
                    FreeNote.NO_KEY,
                    FreeNote.CREATES_IMAGES,
                    FreeNote.WATERMARK,
                    FreeNote.PUBLIC_RESULTS,
                ),
            ),
        ),
        Provider(
            id = "ollama",
            name = "Ollama (locale)",
            baseUrl = "http://10.0.2.2:11434/v1",
            models = listOf("llama3.2", "qwen2.5"),
            builtIn = true,
            freeTier = FreeTier(
                signupUrl = "https://ollama.com/download",
                limits = "quanto regge il computer su cui gira",
                notes = listOf(FreeNote.NEEDS_LOCAL_MACHINE),
            ),
        ),
    )

    const val DEFAULT_SYSTEM_PROMPT =
        "Sei un assistente attento e diretto. Rispondi nella lingua dell'utente, " +
            "vai al punto senza preamboli e usa il markdown quando aiuta la lettura."
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val providers: List<Provider> = Providers.defaults,
    val activeProviderId: String = "deepseek",
    val activeModel: String = "deepseek-flash",
    val systemPrompt: String = Providers.DEFAULT_SYSTEM_PROMPT,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 0,
    val historyLimit: Int = 30,
    val showReasoning: Boolean = true,
    /** Modello che legge le immagini quando quello in chat non le vede. */
    val visionRef: ModelRef? = null,
    /** Modello che trascrive gli audio allegati. */
    val transcribeRef: ModelRef? = null,
    /** Modello che genera immagini. */
    val imageRef: ModelRef? = null,
    /** Modello che legge le risposte ad alta voce. */
    val speechRef: ModelRef? = null,
    val speechVoice: String = "alloy",
    val imageSize: String = "1024x1024",
    /** Manda le immagini al modello in chat anche se non è tra quelli noti. */
    val forceNativeVision: Boolean = false,
    /** Dove il modello può leggere e scrivere file. */
    val workspace: WorkspaceConfig = WorkspaceConfig(),
    /** Se false, ogni scrittura o eliminazione passa da una conferma. */
    val autonomousTools: Boolean = false,
    /** Tiene vivo il lavoro quando l'app non è in primo piano. */
    val backgroundWork: Boolean = true,
    /** Tetto di sicurezza dell'agente. */
    val agentMaxSteps: Int = 60,
    val agentMaxMinutes: Int = 25,
    /** Quando gli elenchi dei modelli sono stati riallineati l'ultima volta. */
    val modelsRefreshedAt: Long = 0L,
    /**
     * Se il modello per le immagini non è disponibile, ripiega su un
     * generatore gratuito. Spento di default: il risultato ha caratteristiche
     * diverse, e non è una cosa da far succedere alle spalle di chi usa l'app.
     */
    val imageFallback: Boolean = false,
    /** Legge le risposte ad alta voce appena arrivano, senza chiederlo. */
    val autoSpeak: Boolean = false,
    /** Lingua della voce e dell'ascolto; vuoto significa quella del telefono. */
    val voiceLanguage: String = "",
    /** La voce del motore di sintesi, fra quelle installate. */
    val deviceVoice: String = "",
    val voiceRate: Float = 1f,
    val voicePitch: Float = 1f,
    /** Il primo avvio è stato completato (o saltato). */
    val onboardingDone: Boolean = false,
    /** L'avviso sui modelli gratuiti è stato letto. */
    val freeDisclaimerAccepted: Boolean = false,
    /** Provider predefiniti che l'utente ha cancellato: non vanno resuscitati. */
    val removedProviders: Set<String> = emptySet(),
    /** Le competenze disponibili, quelle dell'app più quelle scritte a mano. */
    val skills: List<com.lucio.guzzobot.data.skills.Skill> =
        com.lucio.guzzobot.data.skills.Skills.defaults,
    /** Competenze predefinite cancellate: non vanno riproposte. */
    val removedSkills: Set<String> = emptySet(),
    /** I connettori MCP configurati. */
    val mcpServers: List<com.lucio.guzzobot.data.mcp.McpServer> = emptyList(),
) {
    val activeProvider: Provider?
        get() = providers.firstOrNull { it.id == activeProviderId } ?: providers.firstOrNull()
}

object Roles {
    const val USER = "user"
    const val ASSISTANT = "assistant"
    const val SYSTEM = "system"
    const val TOOL = "tool"
}
