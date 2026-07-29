package online.hanatalk

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import online.hanatalk.client.AiExerciseSvcClient
import online.hanatalk.client.GeneratedExerciseDto
import online.hanatalk.client.GenerationResultDto
import online.hanatalk.client.GrammarPointInputDto
import online.hanatalk.domain.JlptLevel
import online.hanatalk.domain.course.Course
import online.hanatalk.domain.course.CourseRepository
import online.hanatalk.domain.exercise.Exercise
import online.hanatalk.domain.exercise.ExerciseAttemptRepository
import online.hanatalk.domain.exercise.ExerciseRepository
import online.hanatalk.domain.exercise.ExerciseType
import online.hanatalk.domain.lesson.Lesson
import online.hanatalk.domain.lesson.LessonRepository
import online.hanatalk.domain.progress.CompletionSource
import online.hanatalk.service.ExerciseService
import online.hanatalk.service.ProgressService
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class ExerciseServiceTest {
    private val exerciseRepository = mock<ExerciseRepository>()
    private val attemptRepository = mock<ExerciseAttemptRepository>()
    private val lessonRepository = mock<LessonRepository>()
    private val courseRepository = mock<CourseRepository>()
    private val progressService = mock<ProgressService>()
    private val aiExerciseSvcClient = mock<AiExerciseSvcClient>()
    private val service =
        ExerciseService(
            exerciseRepository,
            attemptRepository,
            lessonRepository,
            courseRepository,
            progressService,
            aiExerciseSvcClient,
            ObjectMapper().registerKotlinModule(),
        )

    private val courseId = UUID.randomUUID()
    private val lessonId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val lessonContentJson =
        """{"grammarPoints":[{"title":"g","explanation":"e","examples":[]}],""" +
            """"dialogue":{"title":"t","lines":[]},"cultureNote":{"title":"t","body":"b"}}"""
    private val lesson = Lesson(id = lessonId, courseId = courseId, title = "t", contentJson = lessonContentJson, position = 1)
    private val course = Course(id = courseId, title = "c", jlptLevel = JlptLevel.N5)
    private val expectedGrammarPoints = listOf(GrammarPointInputDto("g", "e"))

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

    @Test
    fun `listByLesson returns existing exercises without calling ai-exercise-svc`() {
        given(exerciseRepository.findByLessonId(lessonId)).willReturn(listOf(mcqExercise()))

        val result = service.listByLesson(lessonId)

        assertEquals(1, result.size)
        verify(aiExerciseSvcClient, never()).generateExercises(any(), any(), any())
    }

    @Test
    fun `listByLesson generates and persists exercises when none exist`() {
        given(exerciseRepository.findByLessonId(lessonId)).willReturn(emptyList())
        given(lessonRepository.findById(lessonId)).willReturn(Optional.of(lesson))
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course))
        val generationResult =
            GenerationResultDto(
                exercises =
                    listOf(
                        GeneratedExerciseDto(ExerciseType.MCQ, "p", listOf("A", "B"), "A"),
                        GeneratedExerciseDto(ExerciseType.FILL_IN_BLANK, "p", null, "b"),
                    ),
            )
        given(aiExerciseSvcClient.generateExercises(lessonId, expectedGrammarPoints, course.jlptLevel.name))
            .willReturn(generationResult)
        whenever(exerciseRepository.saveAll(any<List<Exercise>>())).thenAnswer { it.arguments[0] }

        val result = service.listByLesson(lessonId)

        assertEquals(2, result.size)
        verify(aiExerciseSvcClient).generateExercises(lessonId, expectedGrammarPoints, course.jlptLevel.name)
    }

    @Test
    fun `listByLesson surfaces an ai-exercise-svc failure as a 5xx instead of an empty list`() {
        given(exerciseRepository.findByLessonId(lessonId)).willReturn(emptyList())
        given(lessonRepository.findById(lessonId)).willReturn(Optional.of(lesson))
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course))
        given(aiExerciseSvcClient.generateExercises(lessonId, expectedGrammarPoints, course.jlptLevel.name))
            .willThrow(ResponseStatusException(HttpStatus.BAD_GATEWAY, "ai-exercise-svc call failed"))

        val exception = assertThrows<ResponseStatusException> { service.listByLesson(lessonId) }
        assertEquals(HttpStatus.BAD_GATEWAY, exception.statusCode)
        verify(exerciseRepository, never()).saveAll(any<List<Exercise>>())
    }
}
