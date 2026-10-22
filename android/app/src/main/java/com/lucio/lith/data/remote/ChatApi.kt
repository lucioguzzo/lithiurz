package com.lucio.lith.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface StreamEvent {
    data class Content(val text: String) : StreamEvent
    data class Reasoning(val text: String) : StreamEvent
}

class ApiException(message: String) : IOException(message)

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
    ): Flow<StreamEvent> = callbackFlow {
        val payload = ChatRequest(
            model = model,
            messages = messages,
            stream = true,
            temperature = temperature?.toDouble(),
            maxTokens = maxTokens?.takeIf { it > 0 },
        )
        val request = Request.Builder()
            .url(endpoint(baseUrl, "chat/completions"))
            .headers(headers(apiKey))
            .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA))
            .build()

        val call = client.newCall(request)
        launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw ApiException(errorMessage(response.code, response.body?.string()))
                    }
                    val source = response.body?.source() ?: throw ApiException("Risposta vuota")
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
                        (delta.reasoningContent ?: delta.reasoning)?.takeIf { it.isNotEmpty() }?.let {
                            trySend(StreamEvent.Reasoning(it))
                        }
                        delta.content?.takeIf { it.isNotEmpty() }?.let {
                            trySend(StreamEvent.Content(it))
                        }
                    }
                }
                close()
            } catch (e: Throwable) {
                // Una cancellazione dall'utente chiude la call: non è un errore.
                if (call.isCanceled()) close() else close(e)
            }
        }
        awaitClose { call.cancel() }
    }

    suspend fun listModels(baseUrl: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint(baseUrl, "models"))
            .headers(headers(apiKey))
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(errorMessage(response.code, body))
            json.decodeFromString<ModelsResponse>(body).data.map { it.id }.sorted()
        }
    }

    private fun headers(apiKey: String) = okhttp3.Headers.Builder().apply {
        add("Content-Type", "application/json")
        add("Accept", "text/event-stream")
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

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
