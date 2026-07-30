package online.hanatalk.api.dto

import java.time.Instant

data class ReviewResultRequest(
    val correct: Boolean,
)

data class VocabularyReviewResponse(
    val nextReviewAt: Instant,
    val intervalDays: Int,
    val correctStreak: Int,
)
