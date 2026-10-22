package com.lucio.lith.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiMessage(
    val role: String,
    val content: String,
)

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
