package online.hanatalk.domain.vocabulary

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "vocabulary_items")
class VocabularyItem(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "lesson_id", nullable = false)
    val lessonId: UUID,
    @Column(nullable = false, columnDefinition = "TEXT")
    val japanese: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val reading: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val meaning: String,
    @Column(nullable = false)
    val position: Int,
)
