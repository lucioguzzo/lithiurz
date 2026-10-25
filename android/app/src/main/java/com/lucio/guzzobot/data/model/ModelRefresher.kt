package com.lucio.guzzobot.data.model

import com.lucio.guzzobot.data.prefs.SecretStore
import com.lucio.guzzobot.data.prefs.SettingsStore
import com.lucio.guzzobot.data.remote.ChatApi

/**
 * Tiene allineato l'elenco dei modelli con quello che i provider servono
 * davvero: li interroga, sostituisce le sigle sparite e, se il modello in uso
 * non esiste più, passa al parente più prossimo invece di lasciare l'app con
 * un nome morto in mano.
 */
class ModelRefresher(
    private val chatApi: ChatApi,
    private val settingsStore: SettingsStore,
    private val secretStore: SecretStore,
) {

    data class Report(
        val updated: List<String> = emptyList(),
        val failed: List<String> = emptyList(),
        val activeModelChanged: Pair<String, String>? = null,
    ) {
        val isEmpty: Boolean get() = updated.isEmpty() && activeModelChanged == null
    }

    suspend fun refresh(settings: AppSettings, onlyProviderId: String? = null): Report {
        val targets = settings.providers.filter { provider ->
            (onlyProviderId == null || provider.id == onlyProviderId) && usable(provider)
        }
        if (targets.isEmpty()) return Report()

        val updated = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val providers = settings.providers.toMutableList()

        targets.forEach { provider ->
            val fetched = runCatching {
                chatApi.listModels(provider.baseUrl, secretStore.apiKey(provider.id))
            }.getOrElse {
                failed += provider.name
                return@forEach
            }
            val merged = ModelCatalog.merge(provider.models, fetched)
            if (merged != provider.models && merged.isNotEmpty()) {
                providers[providers.indexOfFirst { it.id == provider.id }] =
                    provider.copy(models = merged)
                updated += provider.name
            }
        }

        if (updated.isEmpty()) {
            settingsStore.setModelsRefreshedAt(System.currentTimeMillis())
            return Report(failed = failed)
        }
        settingsStore.saveProviders(providers)

        // Il modello in uso potrebbe essere stato ritirato mentre guardavamo.
        val active = providers.firstOrNull { it.id == settings.activeProviderId }
        var changed: Pair<String, String>? = null
        if (active != null && active.models.isNotEmpty() &&
            settings.activeModel !in active.models
        ) {
            val curated = Providers.defaults.firstOrNull { it.id == active.id }?.models.orEmpty()
            val replacement = ModelCatalog.replacementFor(settings.activeModel, active.models)
                ?: ModelCatalog.preferredChat(active.models, curated)
            if (replacement != null) {
                settingsStore.setActiveModel(active.id, replacement)
                changed = settings.activeModel to replacement
            }
        }
        settingsStore.setModelsRefreshedAt(System.currentTimeMillis())
        return Report(updated = updated, failed = failed, activeModelChanged = changed)
    }

    private fun usable(provider: Provider): Boolean =
        secretStore.hasKey(provider.id) ||
            !provider.baseUrl.startsWith("https://", ignoreCase = true)
}
