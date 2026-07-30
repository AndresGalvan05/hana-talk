package online.hanatalk.domain.vocabulary

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface VocabularyItemRepository : JpaRepository<VocabularyItem, UUID> {
    fun findByLessonIdOrderByPosition(lessonId: UUID): List<VocabularyItem>

    fun findByLessonIdIn(lessonIds: List<UUID>): List<VocabularyItem>
}
