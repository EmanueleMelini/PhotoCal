package it.emanuelemelini.photocal.data.ai.anthropic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// DTOs for the Messages API responses. Only the fields the app uses; the request is built
// as a JsonObject because its content blocks are polymorphic.

@Serializable
data class MessageResponse(
    val content: List<ContentBlock> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
    @SerialName("stop_details") val stopDetails: StopDetails? = null,
)

/** Text blocks carry the answer; thinking blocks are skipped. */
@Serializable
data class ContentBlock(
    val type: String,
    val text: String? = null,
)

/** Set only when stop_reason is "refusal". */
@Serializable
data class StopDetails(
    val category: String? = null,
    val explanation: String? = null,
)

@Serializable
data class ModelInfo(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class ErrorResponse(
    val error: ErrorBody? = null,
)

@Serializable
data class ErrorBody(
    val type: String = "",
    val message: String = "",
)
