package online.hanatalk.api.dto

import jakarta.validation.constraints.NotBlank
import online.hanatalk.domain.exercise.Exercise
import online.hanatalk.domain.exercise.ExerciseType
import java.util.UUID

data class ExerciseResponse(
    val id: UUID,
    val lessonId: UUID,
    val type: ExerciseType,
    val prompt: String,
    val options: List<String>?,
)

data class AttemptRequest(
    @field:NotBlank val answer: String,
)

data class AttemptResponse(
    val correct: Boolean,
)

fun Exercise.toResponse(options: List<String>?) = ExerciseResponse(id, lessonId, type, prompt, options)
