package com.lucio.guzzobot.data.remote

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface StreamEvent {
    data class Content(val text: String) : StreamEvent
    data class Reasoning(val text: String) : StreamEvent

    /** Il modello chiede di eseguire degli strumenti prima di rispondere. */
    data class ToolRequests(val calls: List<ToolCall>) : StreamEvent

    /** Il modello non sa usare strumenti: si è riprovato senza. */
    data object ToolsDropped : StreamEvent
}

/** Una chiamata a uno strumento, ricomposta dai frammenti dello streaming. */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
    /** Da restituire tale e quale al provider: vedi ApiToolCall.extraContent. */
    val extra: JsonObject? = null,
)

class ApiException(message: String, val code: Int = 0) : IOException(message)

/** Client per endpoint compatibili con l'API OpenAI. */
class ChatApi(private val json: Json) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun streamChat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ApiMessage>,
        temperature: Float?,
        maxTokens: Int?,
        tools: kotlinx.serialization.json.JsonArray? = null,
    ): Flow<StreamEvent> = callbackFlow {
        val payload = ChatRequest(
            model = model,
            messages = messages,
            stream = true,
            temperature = temperature?.toDouble(),
            maxTokens = maxTokens?.takeIf { it > 0 },
            tools = tools,
        )
        fun request(withTools: kotlinx.serialization.json.JsonArray?) = Request.Builder()
            .url(endpoint(baseUrl, "chat/completions"))
            .headers(headers(apiKey, accept = "text/event-stream"))
            .post(
                json.encodeToString(payload.copy(tools = withTools)).toRequestBody(JSON_MEDIA),
            )
            .build()

        var call = client.newCall(request(tools))
        launch(Dispatchers.IO) {
            try {
                var attempts = 0
                var currentTools = tools
                while (true) {
                    val retry = call.execute().use { response ->
                        if (response.isSuccessful) {
                            stream(response, this@callbackFlow)
                            return@use false
                        }
                        val body = response.body?.string()
                        when {
                            // «Riprova fra poco» lo dice il provider: non è un
                            // guasto da mostrare, è una coda da aspettare.
                            ApiErrors.transient(response.code, body) && attempts < MAX_RETRIES -> {
                                attempts++
                                true
                            }
                            // Quasi tutti i modelli gratuiti più piccoli non
                            // sanno usare strumenti. Rispondere «errore»
                            // sarebbe assurdo: parlare lo sanno comunque.
                            currentTools != null && ApiErrors.toolsUnsupported(body) -> {
                                send(StreamEvent.ToolsDropped)
                                currentTools = null
                                true
                            }
                            ApiErrors.transient(response.code, body) ->
                                throw ApiException(ApiErrors.exhausted(response.code), response.code)
                            else ->
                                throw ApiException(errorMessage(response.code, body), response.code)
                        }
                    }
                    if (!retry) break
                    // L'attesa cresce a ogni tentativo, per non insistere
                    // addosso a un servizio già in affanno.
                    if (attempts > 0) kotlinx.coroutines.delay(1_200L * attempts * attempts)
                    call = client.newCall(request(currentTools))
                }
                close()
            } catch (e: Throwable) {
                // Una cancellazione dall'utente chiude la call: non è un errore.
                if (call.isCanceled()) close() else close(e)
            }
        }
        awaitClose { call.cancel() }
    }


    /**
     * Legge il flusso di eventi e consegna quello che arriva.
     *
     * Sta in una funzione a parte perché si usa due volte: al primo tentativo
     * e, quando il modello rifiuta gli strumenti, a quello senza.
     */
    private suspend fun stream(
        response: okhttp3.Response,
        scope: kotlinx.coroutines.channels.ProducerScope<StreamEvent>,
    ) {
                val source = response.body?.source() ?: throw ApiException("Risposta vuota")
                // I frammenti di una chiamata a strumento arrivano spezzati:
                // si ricompongono per indice prima di consegnarli.
                val pendingTools = linkedMapOf<Int, PartialToolCall>()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    val data = when {
                        line.startsWith("data:") -> line.removePrefix("data:").trim()
                        line.isBlank() -> continue
                        // Alcuni gateway rispondono in JSON semplice su errore.
                        line.startsWith("{") -> line
                        else -> continue
                    }
                    if (data.isEmpty() || data == "[DONE]") continue
                    val chunk = runCatching { json.decodeFromString<ChatChunk>(data) }.getOrNull()
                        ?: continue
                    chunk.error?.let { throw ApiException(it.message ?: "Errore del provider") }
                    val delta = chunk.choices.firstOrNull()?.delta ?: continue
                    // send() e non trySend(): se la UI è in ritardo i token
                    // aspettano invece di essere scartati.
                    (delta.reasoningContent ?: delta.reasoning)?.takeIf { it.isNotEmpty() }?.let {
                        scope.send(StreamEvent.Reasoning(it))
                    }
                    delta.content?.takeIf { it.isNotEmpty() }?.let {
                        scope.send(StreamEvent.Content(it))
                    }
                    delta.toolCalls?.forEach { fragment ->
                        val slot = pendingTools.getOrPut(fragment.index ?: pendingTools.size) {
                            PartialToolCall()
                        }
                        fragment.id?.let { slot.id = it }
                        fragment.function?.name?.let { slot.name = it }
                        fragment.function?.arguments?.let { slot.arguments.append(it) }
                        fragment.extraContent?.let { slot.extra = it }
                    }
                }
                val requested = pendingTools.values
                    .filter { it.name.isNotBlank() }
                    .map { StreamEventToolCall(it) }
                if (requested.isNotEmpty()) scope.send(StreamEvent.ToolRequests(requested))

    }

    /** Chiamata singola senza streaming: la usiamo per far descrivere le immagini. */
    suspend fun complete(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ApiMessage>,
        maxTokens: Int? = null,
    ): String = withContext(Dispatchers.IO) {
        val payload = ChatRequest(
            model = model,
            messages = messages,
            stream = false,
            temperature = null,
            maxTokens = maxTokens,
        )
        val request = Request.Builder()
            .url(endpoint(baseUrl, "chat/completions"))
            .headers(headers(apiKey))
            .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(errorMessage(response.code, body))
            val parsed = json.decodeFromString<ChatChunk>(body)
            val choice = parsed.choices.firstOrNull()
            (choice?.message?.content ?: choice?.delta?.content).orEmpty().trim()
        }
    }

    /** POST multipart su /audio/transcriptions (Whisper e compatibili). */
    suspend fun transcribe(
        baseUrl: String,
        apiKey: String,
        model: String,
        file: File,
        mime: String,
        language: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "json")
            .apply { if (!language.isNullOrBlank()) addFormDataPart("language", language) }
            .addFormDataPart("file", file.name, file.asRequestBody(mime.toMediaType()))
            .build()
        val request = Request.Builder()
            .url(endpoint(baseUrl, "audio/transcriptions"))
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(errorMessage(response.code, raw))
            runCatching { json.decodeFromString<TranscriptionResponse>(raw).text }
                .getOrDefault(raw)
                .trim()
        }
    }

    /** POST su /images/generations; restituisce i byte delle immagini prodotte. */
    suspend fun generateImage(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        size: String?,
    ): List<ByteArray> = withContext(Dispatchers.IO) {
        val base = ImageRequest(
            model = model,
            prompt = prompt,
            size = size?.takeIf { it.isNotBlank() },
            responseFormat = "b64_json",
        )
        // gpt-image-1 e altri rifiutano response_format: al primo rifiuto
        // riproviamo senza, perché quei modelli restituiscono già base64.
        val images = try {
            retrying { requestImages(baseUrl, apiKey, base).getOrThrow() }
        } catch (error: ApiException) {
            if (error.message?.contains("response_format", true) == true) {
                retrying {
                    requestImages(baseUrl, apiKey, base.copy(responseFormat = null)).getOrThrow()
                }
            } else {
                throw error
            }
        }
        if (images.isEmpty()) throw ApiException("Il provider non ha restituito immagini")
        images.mapNotNull { image ->
            when {
                image.b64Json != null -> runCatching {
                    android.util.Base64.decode(image.b64Json, android.util.Base64.DEFAULT)
                }.getOrNull()

                image.url != null -> download(image.url)
                else -> null
            }
        }
    }

    /** POST su /audio/speech: testo in ingresso, mp3 in uscita. */
    suspend fun speech(
        baseUrl: String,
        apiKey: String,
        model: String,
        voice: String,
        text: String,
    ): ByteArray = withContext(Dispatchers.IO) {
        val payload = SpeechRequest(model = model, input = text, voice = voice)
        val request = Request.Builder()
            .url(endpoint(baseUrl, "audio/speech"))
            .headers(headers(apiKey))
            .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException(errorMessage(response.code, response.body?.string()))
            }
            response.body?.bytes() ?: throw ApiException("Risposta audio vuota")
        }
    }

    /**
     * Pollinations: una GET il cui percorso è il prompt, e che risponde
     * direttamente con i byte dell'immagine. Niente chiave, niente JSON.
     */
    suspend fun generatePollinations(
        baseUrl: String,
        prompt: String,
        size: String?,
    ): List<ByteArray> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(Pollinations.url(prompt = prompt, size = size, baseUrl = baseUrl))
            .get()
            .build()
        retrying {
        client.newCall(request).execute().use { response ->
            // 402 qui non vuol dire «paga»: vuol dire «stai andando troppo in fretta».
            if (response.code == 402 || response.code == 429) {
                throw ApiException(Pollinations.waitMessage(response.header("Retry-After")))
            }
            if (!response.isSuccessful) {
                throw ApiException(errorMessage(response.code, response.body?.string()))
            }
            val bytes = response.body?.bytes()
            if (bytes == null || bytes.isEmpty()) {
                throw ApiException("Pollinations non ha restituito un'immagine")
            }
            listOf(bytes)
        }
        }
    }

    /**
     * Modifica un'immagine esistente con l'API nativa di Google.
     *
     * Due forme di richiesta, provate in quest'ordine: quella corrente e la
     * precedente. Convivono, e un endpoint che risponde «non esiste» non deve
     * far fallire la modifica.
     */
    suspend fun editImage(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        images: List<GoogleImages.Picture>,
    ): List<ByteArray> = withContext(Dispatchers.IO) {
        val base = GoogleImages.nativeBase(baseUrl)
        val attempts = listOf(
            "$base/interactions" to
                GoogleImages.interactionsBody(model, prompt, images, "image/png"),
            "$base/models/$model:generateContent" to
                GoogleImages.generateContentBody(prompt, images),
        )
        var failure: Exception? = null
        attempts.forEach { (url, body) ->
            val outcome = runCatching { retrying { postForImages(url, apiKey, body) } }
            outcome.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
            outcome.exceptionOrNull()?.let { failure = it as? Exception ?: ApiException(it.message.orEmpty()) }
        }
        throw failure ?: ApiException("Il modello non ha restituito un'immagine")
    }

    private fun postForImages(
        url: String,
        apiKey: String,
        body: JsonObject,
    ): List<ByteArray> {
        val request = Request.Builder()
            .url(url)
            // L'API nativa vuole la chiave in questa intestazione, non in Bearer.
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(errorMessage(response.code, raw), response.code)
            }
            val parsed = runCatching { json.parseToJsonElement(raw) }.getOrNull()
                ?: throw ApiException("Risposta non interpretabile")
            return GoogleImages.extractImages(parsed).mapNotNull { encoded ->
                runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()
            }
        }
    }


    /**
     * Riprova quando il provider dice che è questione di attesa.
     *
     * Vale per le immagini quanto per la chat: un 503 «high demand» non è un
     * errore da mostrare all'utente, è una coda. Dopo i tentativi previsti si
     * lascia passare l'errore, con un messaggio che dice cosa è successo
     * invece del JSON del provider.
     */
    private suspend fun <T> retrying(block: () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (error: ApiException) {
                if (!ApiErrors.transient(error.code, error.message) || attempt >= MAX_RETRIES) {
                    if (ApiErrors.transient(error.code, error.message)) {
                        throw ApiException(ApiErrors.exhausted(error.code), error.code)
                    }
                    throw error
                }
                attempt++
                kotlinx.coroutines.delay(1_200L * attempt * attempt)
            }
        }
    }

    private fun requestImages(
        baseUrl: String,
        apiKey: String,
        payload: ImageRequest,
    ): Result<List<ImageData>> = runCatching {
        val request = Request.Builder()
            .url(endpoint(baseUrl, "images/generations"))
            .headers(headers(apiKey))
            .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(errorMessage(response.code, raw), response.code)
            }
            json.decodeFromString<ImageResponse>(raw).data
        }
    }

    private fun download(url: String): ByteArray? = runCatching {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (response.isSuccessful) response.body?.bytes() else null
        }
    }.getOrNull()

    suspend fun listModels(baseUrl: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint(baseUrl, "models"))
            .headers(headers(apiKey))
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(errorMessage(response.code, body))
            json.decodeFromString<ModelsResponse>(body).data
                // Gemini restituisce "models/gemini-3.8-flash": il prefisso
                // non serve a chi legge e non va nel selettore.
                .map { it.id.removePrefix("models/") }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }
    }

    private fun headers(apiKey: String, accept: String = "application/json") =
        okhttp3.Headers.Builder().apply {
            add("Content-Type", "application/json")
            add("Accept", accept)
            if (apiKey.isNotBlank()) add("Authorization", "Bearer $apiKey")
        }.build()

    private fun endpoint(baseUrl: String, path: String): String =
        baseUrl.trim().trimEnd('/') + "/" + path

    private fun errorMessage(code: Int, body: String?): String {
        val parsed = body?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { json.decodeFromString<ErrorEnvelope>(raw) }.getOrNull()
        }
        val detail = parsed?.error?.message ?: parsed?.message ?: parsed?.detail
            ?: body?.take(200)?.takeIf { it.isNotBlank() }
        val hint = when (code) {
            401, 403 -> "Chiave API non valida o mancante."
            402 -> "Credito esaurito sul provider."
            404 -> "Modello o endpoint inesistente: controlla base URL e nome del modello."
            429 -> "Troppe richieste: riprova tra poco."
            in 500..599 -> "Il provider ha restituito un errore temporaneo."
            else -> null
        }
        return listOfNotNull("HTTP $code", hint, detail).joinToString(" · ")
    }

    private class PartialToolCall(
        var id: String = "",
        var name: String = "",
        val arguments: StringBuilder = StringBuilder(),
        var extra: JsonObject? = null,
    )

    private fun StreamEventToolCall(partial: PartialToolCall) = ToolCall(
        id = partial.id.ifBlank { "call_${partial.name}" },
        name = partial.name,
        arguments = partial.arguments.toString(),
        extra = partial.extra,
    )

    private companion object {
        /** Oltre questi non è più una coda: è un servizio che non c'è. */
        const val MAX_RETRIES = 2

        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
