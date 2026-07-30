package online.hanatalk.domain.vocabulary

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "user_vocabulary_progress")
class UserVocabularyProgress(
    @EmbeddedId
    val id: UserVocabularyProgressId,
    @Column(name = "next_review_at", nullable = false)
    var nextReviewAt: Instant,
    @Column(name = "interval_days", nullable = false)
    var intervalDays: Int,
    @Column(name = "correct_streak", nullable = false)
    var correctStreak: Int,
)
