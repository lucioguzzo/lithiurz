package com.lucio.guzzobot.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Il campo `content` dell'API è una stringa per i messaggi di solo testo e un
 * array di parti quando ci sono immagini: lo teniamo come JsonElement e lo
 * costruiamo con [Content].
 */
@Serializable
data class ApiMessage(
    val role: String,
    val content: JsonElement,
)

object Content {
    fun text(value: String): JsonElement = JsonPrimitive(value)

    fun parts(text: String?, imageUrls: List<String>): JsonElement = buildJsonArray {
        if (!text.isNullOrBlank()) {
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", text)
                },
            )
        }
        imageUrls.forEach { url ->
            add(
                buildJsonObject {
                    put("type", "image_url")
                    put("image_url", buildJsonObject { put("url", url) })
                },
            )
        }
    }
}

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val stream: Boolean = true,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
)

@Serializable
data class ChatChunk(
    val choices: List<ChunkChoice> = emptyList(),
    val error: ApiError? = null,
)

@Serializable
data class ChunkChoice(
    val delta: ChunkDelta? = null,
    val message: ChunkDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ChunkDelta(
    val content: String? = null,
    // DeepSeek espone la catena di ragionamento qui, OpenRouter in "reasoning".
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    val reasoning: String? = null,
)

@Serializable
data class ApiError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
)

@Serializable
data class ErrorEnvelope(
    val error: ApiError? = null,
    val message: String? = null,
    val detail: String? = null,
)

@Serializable
data class ModelsResponse(
    val data: List<ModelInfo> = emptyList(),
)

@Serializable
data class ModelInfo(
    val id: String,
)

@Serializable
data class TranscriptionResponse(
    val text: String = "",
)

@Serializable
data class ImageRequest(
    val model: String,
    val prompt: String,
    val n: Int = 1,
    val size: String? = null,
    @SerialName("response_format") val responseFormat: String? = null,
)

@Serializable
data class ImageResponse(
    val data: List<ImageData> = emptyList(),
)

@Serializable
data class ImageData(
    @SerialName("b64_json") val b64Json: String? = null,
    val url: String? = null,
    @SerialName("revised_prompt") val revisedPrompt: String? = null,
)

@Serializable
data class SpeechRequest(
    val model: String,
    val input: String,
    val voice: String,
    @SerialName("response_format") val responseFormat: String = "mp3",
)
