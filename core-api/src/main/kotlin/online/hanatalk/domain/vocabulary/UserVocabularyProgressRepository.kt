package online.hanatalk.domain.vocabulary

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserVocabularyProgressRepository : JpaRepository<UserVocabularyProgress, UserVocabularyProgressId> {
    fun findByIdUserId(userId: UUID): List<UserVocabularyProgress>
}
