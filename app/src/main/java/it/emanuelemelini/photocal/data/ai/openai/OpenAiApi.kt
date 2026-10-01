package it.emanuelemelini.photocal.data.ai.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// DTOs for the Chat Completions responses. Only the fields the app uses; the request is
// built as a JsonObject because its message content is polymorphic.

@Serializable
data class ChatCompletion(
    val choices: List<Choice> = emptyList(),
)

@Serializable
data class Choice(
    val message: Message? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class Message(
    val content: String? = null,
    /** Set instead of content when the model refuses. */
    val refusal: String? = null,
)

@Serializable
data class ModelList(
    val data: List<ModelInfo> = emptyList(),
)

@Serializable
data class ModelInfo(
    val id: String,
)

@Serializable
data class ErrorResponse(
    val error: ErrorBody? = null,
)

@Serializable
data class ErrorBody(
    val message: String = "",
    /** A string on OpenAI ("insufficient_quota"), a number on some compatible services. */
    val code: JsonElement? = null,
)
