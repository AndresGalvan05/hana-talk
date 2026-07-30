package online.hanatalk.client

import com.fasterxml.jackson.annotation.JsonProperty
import online.hanatalk.domain.exercise.ExerciseType

data class GrammarPointInputDto(
    val title: String,
    val explanation: String,
)

data class GenerateExercisesRequest(
    @JsonProperty("lesson_id") val lessonId: String,
    @JsonProperty("grammar_points") val grammarPoints: List<GrammarPointInputDto>,
    @JsonProperty("jlpt_level") val jlptLevel: String,
)

data class GeneratedExerciseDto(
    val type: ExerciseType,
    val prompt: String,
    val options: List<String>?,
    @JsonProperty("correct_answer") val correctAnswer: String,
)

data class GenerationResultDto(
    val exercises: List<GeneratedExerciseDto>,
)

data class ChatMessageDto(
    val speaker: String,
    val japanese: String,
)

data class ChatRequestDto(
    @JsonProperty("jlpt_level") val jlptLevel: String,
    val history: List<ChatMessageDto>,
    val message: String,
)

data class ChatReplyDto(
    val japanese: String,
    val english: String,
    val correction: String?,
)
