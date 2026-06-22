package online.hanatalk.domain.progress

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "user_lesson_progress")
class UserLessonProgress(
    @EmbeddedId
    val id: UserLessonProgressId,
    @Column(name = "completed_at", nullable = false, updatable = false)
    val completedAt: Instant = Instant.now(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val source: CompletionSource,
)
