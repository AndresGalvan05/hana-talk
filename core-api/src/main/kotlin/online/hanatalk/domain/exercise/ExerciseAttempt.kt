package online.hanatalk.domain.exercise

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "exercise_attempts")
class ExerciseAttempt(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    @Column(name = "exercise_id", nullable = false)
    val exerciseId: UUID,
    @Column(name = "submitted_answer", nullable = false, columnDefinition = "TEXT")
    val submittedAnswer: String,
    @Column(name = "is_correct", nullable = false)
    val isCorrect: Boolean,
    @Column(name = "attempted_at", nullable = false, updatable = false)
    val attemptedAt: Instant = Instant.now(),
)
