package online.hanatalk.api.dto

import java.util.UUID

data class CourseProgressResponse(
    val completed: Int,
    val total: Int,
    val completedLessonIds: List<UUID>,
)
