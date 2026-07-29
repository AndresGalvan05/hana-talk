package online.hanatalk.service

import com.fasterxml.jackson.databind.ObjectMapper
import online.hanatalk.api.dto.LessonContent
import online.hanatalk.api.dto.LessonRequest
import online.hanatalk.api.dto.LessonResponse
import online.hanatalk.api.dto.toResponse
import online.hanatalk.domain.course.CourseRepository
import online.hanatalk.domain.lesson.Lesson
import online.hanatalk.domain.lesson.LessonRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class LessonService(
    private val lessonRepository: LessonRepository,
    private val courseRepository: CourseRepository,
    private val objectMapper: ObjectMapper,
) {
    fun listForCourse(courseId: UUID): List<LessonResponse> {
        ensureCourseExists(courseId)
        return lessonRepository.findByCourseIdOrderByPosition(courseId).map { it.toResponseWithContent() }
    }

    fun get(
        courseId: UUID,
        lessonId: UUID,
    ): LessonResponse {
        ensureCourseExists(courseId)
        return lessonRepository.findByIdOrNull(lessonId)
            ?.takeIf { it.courseId == courseId }
            ?.toResponseWithContent()
            ?: throw lessonNotFound()
    }

    fun create(
        courseId: UUID,
        request: LessonRequest,
    ): LessonResponse {
        ensureCourseExists(courseId)
        val lesson =
            Lesson(
                courseId = courseId,
                title = request.title,
                contentJson = objectMapper.writeValueAsString(request.content),
                position = request.position,
            )
        return lessonRepository.save(lesson).toResponse(request.content)
    }

    fun update(
        courseId: UUID,
        lessonId: UUID,
        request: LessonRequest,
    ): LessonResponse {
        ensureCourseExists(courseId)
        val lesson =
            lessonRepository.findByIdOrNull(lessonId)
                ?.takeIf { it.courseId == courseId }
                ?: throw lessonNotFound()
        lesson.title = request.title
        lesson.contentJson = objectMapper.writeValueAsString(request.content)
        lesson.position = request.position
        return lessonRepository.save(lesson).toResponse(request.content)
    }

    fun delete(
        courseId: UUID,
        lessonId: UUID,
    ) {
        ensureCourseExists(courseId)
        val lesson =
            lessonRepository.findByIdOrNull(lessonId)
                ?.takeIf { it.courseId == courseId }
                ?: throw lessonNotFound()
        lessonRepository.delete(lesson)
    }

    private fun ensureCourseExists(courseId: UUID) {
        if (!courseRepository.existsById(courseId)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found")
    }

    private fun lessonNotFound() = ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found")

    private fun Lesson.toResponseWithContent(): LessonResponse {
        val content = objectMapper.readValue(contentJson, LessonContent::class.java)
        return toResponse(content)
    }
}
