package online.hanatalk

import online.hanatalk.domain.progress.CompletionSource
import online.hanatalk.domain.progress.UserLessonProgress
import online.hanatalk.domain.progress.UserLessonProgressId
import online.hanatalk.domain.progress.UserLessonProgressRepository
import online.hanatalk.domain.vocabulary.UserVocabularyProgress
import online.hanatalk.domain.vocabulary.UserVocabularyProgressId
import online.hanatalk.domain.vocabulary.UserVocabularyProgressRepository
import online.hanatalk.domain.vocabulary.VocabularyItem
import online.hanatalk.domain.vocabulary.VocabularyItemRepository
import online.hanatalk.service.VocabularyReviewService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

class VocabularyReviewServiceTest {
    private val vocabularyItemRepository = mock<VocabularyItemRepository>()
    private val userLessonProgressRepository = mock<UserLessonProgressRepository>()
    private val userVocabularyProgressRepository = mock<UserVocabularyProgressRepository>()
    private val service =
        VocabularyReviewService(vocabularyItemRepository, userLessonProgressRepository, userVocabularyProgressRepository)

    private val userId = UUID.randomUUID()
    private val completedLessonId = UUID.randomUUID()
    private val incompleteLessonId = UUID.randomUUID()

    private fun vocabItem(
        id: UUID = UUID.randomUUID(),
        lessonId: UUID = completedLessonId,
    ) = VocabularyItem(id = id, lessonId = lessonId, japanese = "が", reading = "ga", meaning = "subject marker", position = 1)

    private fun stubCompletedLessons(vararg lessonIds: UUID) {
        given(userLessonProgressRepository.findByIdUserId(userId)).willReturn(
            lessonIds.map { UserLessonProgress(id = UserLessonProgressId(userId, it), source = CompletionSource.MANUAL) },
        )
    }

    @Test
    fun `due queue excludes vocabulary from a lesson the user has not completed`() {
        stubCompletedLessons(completedLessonId)
        val fromCompleted = vocabItem(lessonId = completedLessonId)
        val fromIncomplete = vocabItem(lessonId = incompleteLessonId)
        given(vocabularyItemRepository.findByLessonIdIn(listOf(completedLessonId))).willReturn(listOf(fromCompleted))
        given(userVocabularyProgressRepository.findByIdUserId(userId)).willReturn(emptyList())

        val result = service.getDueQueue(userId)

        assertEquals(listOf(fromCompleted.id), result.map { it.id })
        assertTrue(fromIncomplete.id !in result.map { it.id })
    }

    @Test
    fun `due queue includes a never-reviewed item from a completed lesson`() {
        stubCompletedLessons(completedLessonId)
        val item = vocabItem()
        given(vocabularyItemRepository.findByLessonIdIn(listOf(completedLessonId))).willReturn(listOf(item))
        given(userVocabularyProgressRepository.findByIdUserId(userId)).willReturn(emptyList())

        val result = service.getDueQueue(userId)

        assertEquals(listOf(item.id), result.map { it.id })
    }

    @Test
    fun `due queue includes a past-due item`() {
        stubCompletedLessons(completedLessonId)
        val item = vocabItem()
        given(vocabularyItemRepository.findByLessonIdIn(listOf(completedLessonId))).willReturn(listOf(item))
        val pastDue =
            UserVocabularyProgress(
                id = UserVocabularyProgressId(userId, item.id),
                nextReviewAt = Instant.now().minus(1, ChronoUnit.DAYS),
                intervalDays = 1,
                correctStreak = 1,
            )
        given(userVocabularyProgressRepository.findByIdUserId(userId)).willReturn(listOf(pastDue))

        val result = service.getDueQueue(userId)

        assertEquals(listOf(item.id), result.map { it.id })
    }

    @Test
    fun `due queue excludes a not-yet-due item`() {
        stubCompletedLessons(completedLessonId)
        val item = vocabItem()
        given(vocabularyItemRepository.findByLessonIdIn(listOf(completedLessonId))).willReturn(listOf(item))
        val notYetDue =
            UserVocabularyProgress(
                id = UserVocabularyProgressId(userId, item.id),
                nextReviewAt = Instant.now().plus(5, ChronoUnit.DAYS),
                intervalDays = 8,
                correctStreak = 3,
            )
        given(userVocabularyProgressRepository.findByIdUserId(userId)).willReturn(listOf(notYetDue))

        val result = service.getDueQueue(userId)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `first-ever correct review sets a 1-day interval`() {
        val itemId = UUID.randomUUID()
        given(vocabularyItemRepository.existsById(itemId)).willReturn(true)
        given(userVocabularyProgressRepository.findById(UserVocabularyProgressId(userId, itemId)))
            .willReturn(Optional.empty())
        whenever(userVocabularyProgressRepository.save(any<UserVocabularyProgress>())).thenAnswer { it.arguments[0] }

        val result = service.submitReview(userId, itemId, correct = true)

        assertEquals(1, result.intervalDays)
        assertEquals(1, result.correctStreak)
    }

    @Test
    fun `correct review doubles an existing interval`() {
        val itemId = UUID.randomUUID()
        given(vocabularyItemRepository.existsById(itemId)).willReturn(true)
        val existing =
            UserVocabularyProgress(
                id = UserVocabularyProgressId(userId, itemId),
                nextReviewAt = Instant.now(),
                intervalDays = 8,
                correctStreak = 3,
            )
        given(userVocabularyProgressRepository.findById(UserVocabularyProgressId(userId, itemId)))
            .willReturn(Optional.of(existing))
        whenever(userVocabularyProgressRepository.save(any<UserVocabularyProgress>())).thenAnswer { it.arguments[0] }

        val result = service.submitReview(userId, itemId, correct = true)

        assertEquals(16, result.intervalDays)
        assertEquals(4, result.correctStreak)
    }

    @Test
    fun `doubling is capped at 90 days`() {
        val itemId = UUID.randomUUID()
        given(vocabularyItemRepository.existsById(itemId)).willReturn(true)
        val existing =
            UserVocabularyProgress(
                id = UserVocabularyProgressId(userId, itemId),
                nextReviewAt = Instant.now(),
                intervalDays = 64,
                correctStreak = 6,
            )
        given(userVocabularyProgressRepository.findById(UserVocabularyProgressId(userId, itemId)))
            .willReturn(Optional.of(existing))
        whenever(userVocabularyProgressRepository.save(any<UserVocabularyProgress>())).thenAnswer { it.arguments[0] }

        val result = service.submitReview(userId, itemId, correct = true)

        assertEquals(90, result.intervalDays)
    }

    @Test
    fun `incorrect review resets interval and streak regardless of prior state`() {
        val itemId = UUID.randomUUID()
        given(vocabularyItemRepository.existsById(itemId)).willReturn(true)
        val existing =
            UserVocabularyProgress(
                id = UserVocabularyProgressId(userId, itemId),
                nextReviewAt = Instant.now(),
                intervalDays = 32,
                correctStreak = 5,
            )
        given(userVocabularyProgressRepository.findById(UserVocabularyProgressId(userId, itemId)))
            .willReturn(Optional.of(existing))
        whenever(userVocabularyProgressRepository.save(any<UserVocabularyProgress>())).thenAnswer { it.arguments[0] }

        val result = service.submitReview(userId, itemId, correct = false)

        assertEquals(1, result.intervalDays)
        assertEquals(0, result.correctStreak)
    }

    @Test
    fun `reviewing a nonexistent vocabulary item throws 404`() {
        val unknownId = UUID.randomUUID()
        given(vocabularyItemRepository.existsById(unknownId)).willReturn(false)

        assertThrows<ResponseStatusException> { service.submitReview(userId, unknownId, correct = true) }
    }
}
