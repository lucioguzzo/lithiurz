package com.lucio.guzzobot.data.agent

import com.lucio.guzzobot.data.files.AttachmentStore
import com.lucio.guzzobot.data.local.ArtifactDao
import com.lucio.guzzobot.data.local.ArtifactEntity
import com.lucio.guzzobot.data.model.AppSettings
import com.lucio.guzzobot.data.model.ImageApi
import com.lucio.guzzobot.data.model.Roles
import com.lucio.guzzobot.data.prefs.SecretStore
import com.lucio.guzzobot.data.model.Provider
import com.lucio.guzzobot.data.remote.ApiErrors
import com.lucio.guzzobot.data.remote.ApiException
import com.lucio.guzzobot.data.remote.ChatApi
import com.lucio.guzzobot.data.repo.ChatRepository
import com.lucio.guzzobot.data.workspace.ArtifactReader

/**
 * Costruisce lo strumento con cui il modello crea un'immagine da sé.
 *
 * Sta qui e non nel ViewModel perché serve a due chiamanti che non si
 * assomigliano: l'agente in primo piano e il lavoro pianificato in background,
 * che di un ViewModel non sa niente. Scriverlo due volte voleva dire due
 * comportamenti diversi al primo cambiamento.
 */
object ImageMaker {


    /** Un'immagine prodotta, e da chi: il ripiego va detto, non nascosto. */
    data class Produced(val bytes: List<ByteArray>, val model: String, val fallback: Boolean)

    /**
     * Genera l'immagine col provider configurato e, se quello non può —
     * sovraccarico o quota finita — ripiega su quello gratuito, ma solo se
     * l'utente l'ha chiesto.
     *
     * Il ripiego non vale per ogni errore: una chiave sbagliata o un prompt
     * rifiutato non si aggiustano cambiando provider, e provarci
     * nasconderebbe il vero motivo.
     */
    suspend fun produce(
        settings: AppSettings,
        chatApi: ChatApi,
        secrets: SecretStore,
        prompt: String,
    ): Produced {
        val ref = settings.imageRef ?: throw ApiException("Nessun modello per le immagini")
        val provider = settings.providers.firstOrNull { it.id == ref.providerId }
            ?: throw ApiException("Provider non configurato")
        return try {
            Produced(call(provider, ref.model, settings, chatApi, secrets, prompt), ref.model, false)
        } catch (error: ApiException) {
            val spare = fallbackProvider(settings, provider)
            if (spare == null || !ApiErrors.unavailable(error.code, error.message)) {
                // Senza il nome del modello un errore del provider è
                // indistinguibile da un guasto dell'app: va detto chi ha
                // risposto così.
                throw ApiException("${ref.model}: ${error.message}", error.code)
            }
            Produced(
                bytes = chatApi.generatePollinations(spare.baseUrl, prompt, settings.imageSize),
                model = spare.name,
                fallback = true,
            )
        }
    }

    private fun fallbackProvider(settings: AppSettings, current: Provider): Provider? {
        if (!settings.imageFallback) return null
        if (current.imageApi == ImageApi.POLLINATIONS) return null
        return settings.providers.firstOrNull { it.imageApi == ImageApi.POLLINATIONS }
    }

    private suspend fun call(
        provider: Provider,
        model: String,
        settings: AppSettings,
        chatApi: ChatApi,
        secrets: SecretStore,
        prompt: String,
    ): List<ByteArray> = if (provider.imageApi == ImageApi.POLLINATIONS) {
        chatApi.generatePollinations(provider.baseUrl, prompt, settings.imageSize)
    } else {
        chatApi.generateImage(
            baseUrl = provider.baseUrl,
            apiKey = secrets.apiKey(provider.id),
            model = model,
            prompt = prompt,
            size = settings.imageSize,
        )
    }

    fun create(
        settings: AppSettings,
        conversationId: Long?,
        chatApi: ChatApi,
        secrets: SecretStore,
        attachments: AttachmentStore,
        repository: ChatRepository,
        artifacts: ArtifactDao,
    ): ImageTools.Maker? {
        val ref = settings.imageRef ?: return null
        val provider = settings.providers.firstOrNull { it.id == ref.providerId } ?: return null
        // Uno strumento che fallirebbe a ogni chiamata è peggio di uno assente:
        // il modello lo proverebbe, e sprecherebbe un passo del suo budget.
        if (provider.requiresKey && !secrets.hasKey(provider.id)) return null
        if (ref.model.isBlank()) return null

        return ImageTools.Maker { prompt ->
            val produced = produce(settings, chatApi, secrets, prompt)
            val saved = produced.bytes.map { attachments.saveGeneratedImage(it, prompt) }
            saved.forEach { image ->
                artifacts.insert(
                    ArtifactEntity(
                        conversationId = conversationId,
                        kind = ArtifactReader.IMAGE,
                        name = prompt.take(60),
                        location = image.path,
                        detail = produced.model,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
            if (conversationId != null) {
                // L'immagine entra nella conversazione come un messaggio suo,
                // altrimenti il modello la descriverebbe senza che si veda.
                repository.addMessage(
                    conversationId = conversationId,
                    role = Roles.ASSISTANT,
                    content = "",
                    model = produced.model,
                    attachments = saved,
                )
            }
            buildString {
                append("Immagine creata e già mostrata all'utente (${saved.size}). ")
                append("Non ridescriverla: la sta vedendo.")
                if (produced.fallback) {
                    append(" Il modello scelto non era disponibile: ")
                    append("è stata creata con ${produced.model}.")
                }
            }
        }
    }
}
