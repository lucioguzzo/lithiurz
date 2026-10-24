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
    /** Se il provider ha un piano gratuito vero, cosa offre e cosa chiede. */
    val freeTier: FreeTier? = null,
    /**
     * Come si riconosce una chiave di questo provider. Serve a non far
     * trascrivere a mano una stringa di quaranta caratteri: se è negli
     * appunti, l'app se ne accorge e la propone.
     */
    val keyPattern: String? = null,
)

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
    val notes: List<FreeNote> = emptyList(),
) {
    /** Un modello è gratuito se lo è tutto il provider o se porta il marcatore. */
    fun covers(model: String): Boolean =
        freeMarker == null || model.contains(freeMarker, ignoreCase = true)
}

/** Quello che un piano gratuito dà in più, o si prende in cambio. */
@Serializable
enum class FreeNote(val warning: Boolean) {
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
                limits = "30 richieste al minuto",
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
                limits = "1 richiesta al secondo, circa 1 miliardo di token al mese",
                notes = listOf(FreeNote.NO_CARD, FreeNote.TRAINS_ON_DATA),
            ),
        ),
        Provider(
            id = "xai",
            keyPattern = "xai-[A-Za-z0-9]{40,}",
            name = "xAI",
            baseUrl = "https://api.x.ai/v1",
            models = listOf("grok-2-latest"),
            keyUrl = "https://console.x.ai",
            builtIn = true,
        ),
        Provider(
            id = "zai",
            name = "Z.ai (GLM)",
            baseUrl = "https://api.z.ai/api/paas/v4",
            // Punto di partenza: l'elenco vero arriva dall'endpoint /models.
            models = listOf("glm-5.4", "glm-5.1", "glm-4.6v"),
            keyUrl = "https://z.ai/manage-apikey/apikey-list",
            builtIn = true,
            freeTier = FreeTier(
                signupUrl = "https://z.ai/manage-apikey/apikey-list",
                limits = "limiti non dichiarati dal provider",
                freeMarker = "flash",
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
            models = listOf("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash-lite"),
            keyUrl = "https://aistudio.google.com/apikey",
            builtIn = true,
            freeTier = FreeTier(
                signupUrl = "https://aistudio.google.com/apikey",
                limits = "limiti per progetto, visibili su aistudio.google.com/rate-limit",
                notes = listOf(FreeNote.NO_CARD, FreeNote.SEES_IMAGES, FreeNote.TRAINS_ON_DATA),
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
            freeTier = FreeTier(
                signupUrl = "https://cloud.cerebras.ai",
                limits = "30 richieste al minuto, finestra ridotta rispetto al piano a pagamento",
                notes = listOf(FreeNote.NO_CARD),
            ),
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
    /** Il primo avvio è stato completato (o saltato). */
    val onboardingDone: Boolean = false,
    /** L'avviso sui modelli gratuiti è stato letto. */
    val freeDisclaimerAccepted: Boolean = false,
    /** Provider predefiniti che l'utente ha cancellato: non vanno resuscitati. */
    val removedProviders: Set<String> = emptySet(),
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
