package it.emanuelemelini.photocal.data.gemini

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// DTOs for the generateContent REST endpoint (v1beta). Only the fields the app uses.

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null,
)

@Serializable
data class Content(
    val parts: List<Part> = emptyList(),
    val role: String? = null,
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null,
    /** true for the "thoughts" of reasoning models: they must be ignored. */
    val thought: Boolean? = null,
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String,
)

@Serializable
data class GenerationConfig(
    val responseMimeType: String,
    val responseSchema: JsonObject,
    val temperature: Double? = null,
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate> = emptyList(),
    val promptFeedback: PromptFeedback? = null,
)

@Serializable
data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null,
)

@Serializable
data class PromptFeedback(
    val blockReason: String? = null,
)

@Serializable
data class ErrorResponse(
    val error: ErrorBody? = null,
)

@Serializable
data class ErrorBody(
    val code: Int = 0,
    val message: String = "",
    val status: String = "",
)
