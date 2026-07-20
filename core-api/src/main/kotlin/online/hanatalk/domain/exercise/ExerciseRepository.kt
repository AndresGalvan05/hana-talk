package online.hanatalk.domain.exercise

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ExerciseRepository : JpaRepository<Exercise, UUID> {
    fun findByLessonId(lessonId: UUID): List<Exercise>
}
