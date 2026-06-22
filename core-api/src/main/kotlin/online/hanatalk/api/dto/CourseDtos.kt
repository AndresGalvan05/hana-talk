package online.hanatalk.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import online.hanatalk.domain.JlptLevel
import online.hanatalk.domain.course.Course
import java.time.Instant
import java.util.UUID

data class CourseRequest(
    @field:NotBlank @field:Size(max = 100) val title: String,
    @field:NotNull val jlptLevel: JlptLevel,
    @field:Size(max = 500) val description: String? = null,
)

data class CourseResponse(
    val id: UUID,
    val title: String,
    val jlptLevel: JlptLevel,
    val description: String?,
    val createdAt: Instant,
)

fun Course.toResponse() = CourseResponse(id, title, jlptLevel, description, createdAt)
