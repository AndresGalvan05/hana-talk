package online.hanatalk.service

import online.hanatalk.api.dto.CourseProgressResponse
import online.hanatalk.domain.course.CourseRepository
import online.hanatalk.domain.lesson.LessonRepository
import online.hanatalk.domain.progress.CompletionSource
import online.hanatalk.domain.progress.UserLessonProgress
import online.hanatalk.domain.progress.UserLessonProgressId
import online.hanatalk.domain.progress.UserLessonProgressRepository
import online.hanatalk.kafka.EventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class ProgressService(
    private val progressRepository: UserLessonProgressRepository,
    private val lessonRepository: LessonRepository,
    private val courseRepository: CourseRepository,
    private val eventPublisher: EventPublisher,
) {
    fun markComplete(
        userId: UUID,
        lessonId: UUID,
        courseId: UUID,
        source: CompletionSource,
    ) {
        val lesson =
            lessonRepository.findByIdOrNull(lessonId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found")
        if (lesson.courseId != courseId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found in this course")
        }
        val progressId = UserLessonProgressId(userId, lessonId)
        if (progressRepository.existsById(progressId)) return
        progressRepository.save(UserLessonProgress(id = progressId, source = source))

        val course = courseRepository.findByIdOrNull(courseId) ?: return
        eventPublisher.publishExerciseCompleted(userId, lessonId, courseId, course.jlptLevel.name, source.name)
    }

    fun getCourseProgress(
        userId: UUID,
        courseId: UUID,
    ): CourseProgressResponse {
        val lessonIds = lessonRepository.findByCourseIdOrderByPosition(courseId).map { it.id }
        val completedIds = progressRepository.findByIdUserId(userId).map { it.id.lessonId }.toSet()
        val completedInCourse = lessonIds.filter { it in completedIds }
        return CourseProgressResponse(
            completed = completedInCourse.size,
            total = lessonIds.size,
            completedLessonIds = completedInCourse,
        )
    }
}
