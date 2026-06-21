package online.hanatalk.service

import online.hanatalk.api.dto.CourseRequest
import online.hanatalk.api.dto.CourseResponse
import online.hanatalk.api.dto.toResponse
import online.hanatalk.domain.Language
import online.hanatalk.domain.course.Course
import online.hanatalk.domain.course.CourseRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class CourseService(private val courseRepository: CourseRepository) {

    fun listAll(language: Language?): List<CourseResponse> =
        (if (language != null) courseRepository.findByLanguage(language) else courseRepository.findAll())
            .map { it.toResponse() }

    fun get(id: UUID): CourseResponse =
        (courseRepository.findByIdOrNull(id) ?: throw notFound()).toResponse()

    fun create(request: CourseRequest): CourseResponse {
        val course = Course(title = request.title, language = request.language, description = request.description)
        return courseRepository.save(course).toResponse()
    }

    fun update(id: UUID, request: CourseRequest): CourseResponse {
        val course = courseRepository.findByIdOrNull(id) ?: throw notFound()
        course.title = request.title
        course.language = request.language
        course.description = request.description
        return courseRepository.save(course).toResponse()
    }

    fun delete(id: UUID) {
        if (!courseRepository.existsById(id)) throw notFound()
        courseRepository.deleteById(id)
    }

    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found")
}
