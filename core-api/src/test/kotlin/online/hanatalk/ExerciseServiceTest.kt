package online.hanatalk

import com.fasterxml.jackson.databind.ObjectMapper
import online.hanatalk.domain.exercise.Exercise
import online.hanatalk.domain.exercise.ExerciseAttemptRepository
import online.hanatalk.domain.exercise.ExerciseRepository
import online.hanatalk.domain.exercise.ExerciseType
import online.hanatalk.domain.lesson.Lesson
import online.hanatalk.domain.lesson.LessonRepository
import online.hanatalk.domain.progress.CompletionSource
import online.hanatalk.service.ExerciseService
import online.hanatalk.service.ProgressService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class ExerciseServiceTest {
    private val exerciseRepository = mock<ExerciseRepository>()
    private val attemptRepository = mock<ExerciseAttemptRepository>()
    private val lessonRepository = mock<LessonRepository>()
    private val progressService = mock<ProgressService>()
    private val service =
        ExerciseService(exerciseRepository, attemptRepository, lessonRepository, progressService, ObjectMapper())

    private val courseId = UUID.randomUUID()
    private val lessonId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val lesson = Lesson(id = lessonId, courseId = courseId, title = "t", content = "c", position = 1)

    private fun mcqExercise(id: UUID = UUID.randomUUID()) =
        Exercise(
            id = id,
            lessonId = lessonId,
            type = ExerciseType.MCQ,
            prompt = "p",
            optionsJson = """["A","B"]""",
            correctAnswer = "A",
        )

    private fun fillInBlankExercise(id: UUID = UUID.randomUUID()) =
        Exercise(id = id, lessonId = lessonId, type = ExerciseType.FILL_IN_BLANK, prompt = "p", correctAnswer = "Sumimasen")

    @Test
    fun `correct MCQ attempt marks the lesson complete via EXERCISE source`() {
        val exercise = mcqExercise()
        given(exerciseRepository.findById(exercise.id)).willReturn(Optional.of(exercise))
        given(lessonRepository.findById(lessonId)).willReturn(Optional.of(lesson))

        val result = service.submitAttempt(userId, exercise.id, "A")

        assertTrue(result.correct)
        verify(progressService).markComplete(userId, lessonId, courseId, CompletionSource.EXERCISE)
        verify(attemptRepository).save(any())
    }

    @Test
    fun `correct fill-in-blank attempt ignores case and surrounding whitespace`() {
        val exercise = fillInBlankExercise()
        given(exerciseRepository.findById(exercise.id)).willReturn(Optional.of(exercise))
        given(lessonRepository.findById(lessonId)).willReturn(Optional.of(lesson))

        val result = service.submitAttempt(userId, exercise.id, "  sumimasen  ")

        assertTrue(result.correct)
    }

    @Test
    fun `incorrect attempt is recorded but does not complete the lesson`() {
        val exercise = mcqExercise()
        given(exerciseRepository.findById(exercise.id)).willReturn(Optional.of(exercise))

        val result = service.submitAttempt(userId, exercise.id, "B")

        assertFalse(result.correct)
        verify(progressService, never()).markComplete(any(), any(), any(), any())
        verify(attemptRepository).save(any())
    }

    @Test
    fun `repeated correct attempts both call markComplete, relying on its own idempotency`() {
        val exercise = mcqExercise()
        given(exerciseRepository.findById(exercise.id)).willReturn(Optional.of(exercise))
        given(lessonRepository.findById(lessonId)).willReturn(Optional.of(lesson))

        service.submitAttempt(userId, exercise.id, "A")
        service.submitAttempt(userId, exercise.id, "A")

        verify(progressService, times(2)).markComplete(userId, lessonId, courseId, CompletionSource.EXERCISE)
        verify(attemptRepository, times(2)).save(any())
    }

    @Test
    fun `attempt on a nonexistent exercise throws 404 and saves nothing`() {
        val unknownId = UUID.randomUUID()
        given(exerciseRepository.findById(unknownId)).willReturn(Optional.empty())

        assertThrows<ResponseStatusException> { service.submitAttempt(userId, unknownId, "anything") }
        verify(attemptRepository, never()).save(any())
    }
}
