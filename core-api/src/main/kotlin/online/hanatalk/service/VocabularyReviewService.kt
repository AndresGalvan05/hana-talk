package online.hanatalk.service

import online.hanatalk.api.dto.VocabularyItemResponse
import online.hanatalk.api.dto.VocabularyReviewResponse
import online.hanatalk.api.dto.toResponse
import online.hanatalk.domain.progress.UserLessonProgressRepository
import online.hanatalk.domain.vocabulary.UserVocabularyProgress
import online.hanatalk.domain.vocabulary.UserVocabularyProgressId
import online.hanatalk.domain.vocabulary.UserVocabularyProgressRepository
import online.hanatalk.domain.vocabulary.VocabularyItemRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val INITIAL_INTERVAL_DAYS = 1
private const val MAX_INTERVAL_DAYS = 90

@Service
class VocabularyReviewService(
    private val vocabularyItemRepository: VocabularyItemRepository,
    private val userLessonProgressRepository: UserLessonProgressRepository,
    private val userVocabularyProgressRepository: UserVocabularyProgressRepository,
) {
    fun getDueQueue(userId: UUID): List<VocabularyItemResponse> {
        val completedLessonIds = userLessonProgressRepository.findByIdUserId(userId).map { it.id.lessonId }
        if (completedLessonIds.isEmpty()) return emptyList()

        val items = vocabularyItemRepository.findByLessonIdIn(completedLessonIds)
        val progressByItemId =
            userVocabularyProgressRepository.findByIdUserId(userId).associateBy { it.id.vocabularyItemId }

        val now = Instant.now()
        return items
            .filter { item ->
                val progress = progressByItemId[item.id]
                progress == null || !progress.nextReviewAt.isAfter(now)
            }.map { it.toResponse() }
    }

    fun submitReview(
        userId: UUID,
        vocabularyItemId: UUID,
        correct: Boolean,
    ): VocabularyReviewResponse {
        if (!vocabularyItemRepository.existsById(vocabularyItemId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Vocabulary item not found")
        }

        val id = UserVocabularyProgressId(userId, vocabularyItemId)
        val existing = userVocabularyProgressRepository.findByIdOrNull(id)

        val newIntervalDays =
            when {
                !correct -> INITIAL_INTERVAL_DAYS
                existing == null -> INITIAL_INTERVAL_DAYS
                else -> minOf(existing.intervalDays * 2, MAX_INTERVAL_DAYS)
            }
        val newStreak = if (correct) (existing?.correctStreak ?: 0) + 1 else 0
        val nextReviewAt = Instant.now().plus(newIntervalDays.toLong(), ChronoUnit.DAYS)

        val progress =
            existing?.apply {
                intervalDays = newIntervalDays
                correctStreak = newStreak
                this.nextReviewAt = nextReviewAt
            } ?: UserVocabularyProgress(
                id = id,
                nextReviewAt = nextReviewAt,
                intervalDays = newIntervalDays,
                correctStreak = newStreak,
            )
        userVocabularyProgressRepository.save(progress)

        return VocabularyReviewResponse(nextReviewAt, newIntervalDays, newStreak)
    }
}
