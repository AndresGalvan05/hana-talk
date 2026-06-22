package online.hanatalk.domain.progress

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserLessonProgressRepository : JpaRepository<UserLessonProgress, UserLessonProgressId> {
    fun findByIdUserId(userId: UUID): List<UserLessonProgress>
}
