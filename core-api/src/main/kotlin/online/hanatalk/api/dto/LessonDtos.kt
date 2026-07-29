package online.hanatalk.api.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import online.hanatalk.domain.lesson.Lesson
import java.time.Instant
import java.util.UUID

data class LessonRequest(
    @field:NotBlank @field:Size(max = 100) val title: String,
    val content: LessonContent,
    @field:Min(1) val position: Int,
)

data class LessonResponse(
    val id: UUID,
    val courseId: UUID,
    val title: String,
    val content: LessonContent,
    val position: Int,
    val createdAt: Instant,
)

fun Lesson.toResponse(content: LessonContent) = LessonResponse(id, courseId, title, content, position, createdAt)
