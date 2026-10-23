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
)

object Providers {
    val defaults: List<Provider> = listOf(
        Provider(
            id = "deepseek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            // Nomi aggiornati: deepseek-chat e deepseek-reasoner sono stati ritirati.
            models = listOf("deepseek-flash", "deepseek-v4-pro"),
            keyUrl = "https://platform.deepseek.com/api_keys",
            builtIn = true,
        ),
        Provider(
            id = "openrouter",
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
        ),
        Provider(
            id = "groq",
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
        ),
        Provider(
            id = "mistral",
            name = "Mistral",
            baseUrl = "https://api.mistral.ai/v1",
            models = listOf("mistral-large-latest", "mistral-small-latest"),
            keyUrl = "https://console.mistral.ai/api-keys",
            builtIn = true,
        ),
        Provider(
            id = "xai",
            name = "xAI",
            baseUrl = "https://api.x.ai/v1",
            models = listOf("grok-2-latest"),
            keyUrl = "https://console.x.ai",
            builtIn = true,
        ),
        Provider(
            id = "openai",
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
    val activeModel: String = "deepseek-chat",
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
