package com.lucio.guzzobot.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lucio.guzzobot.data.model.AppSettings
import com.lucio.guzzobot.data.model.ModelRef
import com.lucio.guzzobot.data.model.WorkspaceConfig
import com.lucio.guzzobot.data.model.Provider
import com.lucio.guzzobot.data.model.Providers
import com.lucio.guzzobot.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "guzzobot_settings")

class SettingsStore(context: Context, private val json: Json) {

    private val store = context.dataStore

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val providers = stringPreferencesKey("providers")
        val activeProvider = stringPreferencesKey("active_provider")
        val activeModel = stringPreferencesKey("active_model")
        val systemPrompt = stringPreferencesKey("system_prompt")
        val temperature = floatPreferencesKey("temperature")
        val maxTokens = intPreferencesKey("max_tokens")
        val historyLimit = intPreferencesKey("history_limit")
        val showReasoning = booleanPreferencesKey("show_reasoning")
        val visionRef = stringPreferencesKey("vision_ref")
        val transcribeRef = stringPreferencesKey("transcribe_ref")
        val imageRef = stringPreferencesKey("image_ref")
        val speechRef = stringPreferencesKey("speech_ref")
        val speechVoice = stringPreferencesKey("speech_voice")
        val imageSize = stringPreferencesKey("image_size")
        val forceNativeVision = booleanPreferencesKey("force_native_vision")
        val workspace = stringPreferencesKey("workspace")
        val autonomousTools = booleanPreferencesKey("autonomous_tools")
        val backgroundWork = booleanPreferencesKey("background_work")
        val agentMaxSteps = intPreferencesKey("agent_max_steps")
        val agentMaxMinutes = intPreferencesKey("agent_max_minutes")
        val modelsRefreshedAt = androidx.datastore.preferences.core.longPreferencesKey("models_at")
    }

    /** deepseek-chat e deepseek-reasoner sono stati ritirati dal provider. */
    private val retiredModels = mapOf(
        "deepseek-chat" to "deepseek-flash",
        "deepseek-reasoner" to "deepseek-flash",
        "deepseek-v4-flash" to "deepseek-flash",
    )

    private fun current(model: String): String = retiredModels[model] ?: model

    private fun current(providers: List<Provider>): List<Provider> = providers.map { provider ->
        val updated = provider.models.map(::current).distinct()
        if (updated == provider.models) provider else provider.copy(models = updated)
    }

    private fun ref(raw: String?): ModelRef? = raw
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { json.decodeFromString<ModelRef>(it) }.getOrNull() }

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        val stored = prefs[Keys.providers]?.let { raw ->
            runCatching { json.decodeFromString<List<Provider>>(raw) }.getOrNull()
        }
        AppSettings(
            themeMode = prefs[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            providers = stored?.takeIf { it.isNotEmpty() }?.let(::current) ?: Providers.defaults,
            activeProviderId = prefs[Keys.activeProvider] ?: Providers.defaults.first().id,
            activeModel = prefs[Keys.activeModel]?.let(::current)
                ?: Providers.defaults.first().models.first(),
            systemPrompt = prefs[Keys.systemPrompt] ?: Providers.DEFAULT_SYSTEM_PROMPT,
            temperature = prefs[Keys.temperature] ?: 0.7f,
            maxTokens = prefs[Keys.maxTokens] ?: 0,
            historyLimit = prefs[Keys.historyLimit] ?: 30,
            showReasoning = prefs[Keys.showReasoning] ?: true,
            visionRef = ref(prefs[Keys.visionRef]),
            transcribeRef = ref(prefs[Keys.transcribeRef]),
            imageRef = ref(prefs[Keys.imageRef]),
            speechRef = ref(prefs[Keys.speechRef]),
            speechVoice = prefs[Keys.speechVoice] ?: "alloy",
            imageSize = prefs[Keys.imageSize] ?: "1024x1024",
            forceNativeVision = prefs[Keys.forceNativeVision] ?: false,
            workspace = prefs[Keys.workspace]
                ?.let { runCatching { json.decodeFromString<WorkspaceConfig>(it) }.getOrNull() }
                ?: WorkspaceConfig(),
            autonomousTools = prefs[Keys.autonomousTools] ?: false,
            backgroundWork = prefs[Keys.backgroundWork] ?: true,
            agentMaxSteps = prefs[Keys.agentMaxSteps] ?: 60,
            agentMaxMinutes = prefs[Keys.agentMaxMinutes] ?: 25,
            modelsRefreshedAt = prefs[Keys.modelsRefreshedAt] ?: 0L,
        )
    }

    suspend fun setTheme(mode: ThemeMode) = store.edit { it[Keys.theme] = mode.name }

    suspend fun setActiveModel(providerId: String, model: String) = store.edit {
        it[Keys.activeProvider] = providerId
        it[Keys.activeModel] = model
    }

    suspend fun setSystemPrompt(prompt: String) = store.edit { it[Keys.systemPrompt] = prompt }

    suspend fun setTemperature(value: Float) = store.edit { it[Keys.temperature] = value }

    suspend fun setMaxTokens(value: Int) = store.edit { it[Keys.maxTokens] = value }

    suspend fun setHistoryLimit(value: Int) = store.edit { it[Keys.historyLimit] = value }

    suspend fun setShowReasoning(value: Boolean) = store.edit { it[Keys.showReasoning] = value }

    suspend fun setVisionRef(value: ModelRef?) = setRef(Keys.visionRef, value)

    suspend fun setTranscribeRef(value: ModelRef?) = setRef(Keys.transcribeRef, value)

    suspend fun setImageRef(value: ModelRef?) = setRef(Keys.imageRef, value)

    suspend fun setSpeechRef(value: ModelRef?) = setRef(Keys.speechRef, value)

    suspend fun setSpeechVoice(value: String) = store.edit { it[Keys.speechVoice] = value.trim() }

    suspend fun setImageSize(value: String) = store.edit { it[Keys.imageSize] = value.trim() }

    suspend fun setForceNativeVision(value: Boolean) = store.edit {
        it[Keys.forceNativeVision] = value
    }

    suspend fun setWorkspace(config: WorkspaceConfig) = store.edit {
        it[Keys.workspace] = json.encodeToString(config)
    }

    suspend fun setAutonomousTools(value: Boolean) = store.edit {
        it[Keys.autonomousTools] = value
    }

    suspend fun setBackgroundWork(value: Boolean) = store.edit {
        it[Keys.backgroundWork] = value
    }

    suspend fun setModelsRefreshedAt(value: Long) = store.edit {
        it[Keys.modelsRefreshedAt] = value
    }

    suspend fun setAgentBudget(steps: Int, minutes: Int) = store.edit {
        it[Keys.agentMaxSteps] = steps.coerceIn(5, 300)
        it[Keys.agentMaxMinutes] = minutes.coerceIn(1, 120)
    }

    private suspend fun setRef(key: Preferences.Key<String>, value: ModelRef?) = store.edit { prefs ->
        if (value == null) prefs.remove(key) else prefs[key] = json.encodeToString(value)
    }

    suspend fun saveProviders(providers: List<Provider>) = store.edit {
        it[Keys.providers] = json.encodeToString(providers)
    }
}
