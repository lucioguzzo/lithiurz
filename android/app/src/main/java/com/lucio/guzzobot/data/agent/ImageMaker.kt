package com.lucio.guzzobot.data.agent

import com.lucio.guzzobot.data.files.AttachmentStore
import com.lucio.guzzobot.data.local.ArtifactDao
import com.lucio.guzzobot.data.local.ArtifactEntity
import com.lucio.guzzobot.data.model.AppSettings
import com.lucio.guzzobot.data.model.ImageApi
import com.lucio.guzzobot.data.model.Roles
import com.lucio.guzzobot.data.prefs.SecretStore
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
            val bytes = if (provider.imageApi == ImageApi.POLLINATIONS) {
                chatApi.generatePollinations(provider.baseUrl, prompt, settings.imageSize)
            } else {
                chatApi.generateImage(
                    baseUrl = provider.baseUrl,
                    apiKey = secrets.apiKey(provider.id),
                    model = ref.model,
                    prompt = prompt,
                    size = settings.imageSize,
                )
            }
            val saved = bytes.map { attachments.saveGeneratedImage(it, prompt) }
            saved.forEach { image ->
                artifacts.insert(
                    ArtifactEntity(
                        conversationId = conversationId,
                        kind = ArtifactReader.IMAGE,
                        name = prompt.take(60),
                        location = image.path,
                        detail = ref.model,
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
                    model = ref.model,
                    attachments = saved,
                )
            }
            "Immagine creata e già mostrata all'utente (${saved.size}). " +
                "Non ridescriverla: la sta vedendo."
        }
    }
}
