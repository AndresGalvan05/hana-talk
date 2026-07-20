package online.hanatalk.service

import com.fasterxml.jackson.databind.ObjectMapper
import online.hanatalk.api.dto.AttemptResponse
import online.hanatalk.api.dto.ExerciseResponse
import online.hanatalk.api.dto.toResponse
import online.hanatalk.client.AiExerciseSvcClient
import online.hanatalk.client.GeneratedExerciseDto
import online.hanatalk.domain.course.CourseRepository
import online.hanatalk.domain.exercise.Exercise
import online.hanatalk.domain.exercise.ExerciseAttempt
import online.hanatalk.domain.exercise.ExerciseAttemptRepository
import online.hanatalk.domain.exercise.ExerciseRepository
import online.hanatalk.domain.exercise.ExerciseType
import online.hanatalk.domain.lesson.LessonRepository
import online.hanatalk.domain.progress.CompletionSource
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class ExerciseService(
    private val exerciseRepository: ExerciseRepository,
    private val attemptRepository: ExerciseAttemptRepository,
    private val lessonRepository: LessonRepository,
    private val courseRepository: CourseRepository,
    private val progressService: ProgressService,
    private val aiExerciseSvcClient: AiExerciseSvcClient,
    private val objectMapper: ObjectMapper,
) {
    fun listByLesson(lessonId: UUID): List<ExerciseResponse> {
        val existing = exerciseRepository.findByLessonId(lessonId)
        if (existing.isNotEmpty()) {
            return existing.map { it.toResponse(parseOptions(it)) }
        }

        val lesson =
            lessonRepository.findByIdOrNull(lessonId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found")
        val course =
            courseRepository.findByIdOrNull(lesson.courseId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found")

        val result = aiExerciseSvcClient.generateExercises(lessonId, lesson.content, course.jlptLevel.name)
        val generated = exerciseRepository.saveAll(result.exercises.map { it.toEntity(lessonId) })
        return generated.map { it.toResponse(parseOptions(it)) }
    }

    private fun GeneratedExerciseDto.toEntity(lessonId: UUID): Exercise =
        Exercise(
            lessonId = lessonId,
            type = type,
            prompt = prompt,
            optionsJson = options?.let { objectMapper.writeValueAsString(it) },
            correctAnswer = correctAnswer,
        )

    fun submitAttempt(
        userId: UUID,
        exerciseId: UUID,
        answer: String,
    ): AttemptResponse {
        val exercise =
            exerciseRepository.findByIdOrNull(exerciseId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found")

        val correct = grade(exercise, answer)
        attemptRepository.save(
            ExerciseAttempt(userId = userId, exerciseId = exerciseId, submittedAnswer = answer, isCorrect = correct),
        )

        if (correct) {
            val lesson =
                lessonRepository.findByIdOrNull(exercise.lessonId)
                    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found")
            progressService.markComplete(userId, exercise.lessonId, lesson.courseId, CompletionSource.EXERCISE)
        }

        return AttemptResponse(correct = correct)
    }

    private fun grade(
        exercise: Exercise,
        answer: String,
    ): Boolean =
        when (exercise.type) {
            ExerciseType.MCQ -> answer == exercise.correctAnswer
            ExerciseType.FILL_IN_BLANK -> answer.trim().lowercase() == exercise.correctAnswer.trim().lowercase()
        }

    private fun parseOptions(exercise: Exercise): List<String>? =
        exercise.optionsJson?.let { objectMapper.readValue(it, Array<String>::class.java).toList() }
}
