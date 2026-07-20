package online.hanatalk.domain.exercise

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "exercises")
class Exercise(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "lesson_id", nullable = false)
    val lessonId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: ExerciseType,
    @Column(nullable = false, columnDefinition = "TEXT")
    val prompt: String,
    @Column(name = "options_json", columnDefinition = "TEXT")
    val optionsJson: String? = null,
    @Column(name = "correct_answer", nullable = false, columnDefinition = "TEXT")
    val correctAnswer: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
