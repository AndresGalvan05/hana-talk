package online.hanatalk.domain.lesson

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface LessonRepository : JpaRepository<Lesson, UUID> {
    fun findByCourseIdOrderByPosition(courseId: UUID): List<Lesson>
}
