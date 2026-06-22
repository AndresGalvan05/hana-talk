package online.hanatalk.domain.course

import online.hanatalk.domain.JlptLevel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface CourseRepository : JpaRepository<Course, UUID> {
    fun findByJlptLevel(jlptLevel: JlptLevel): List<Course>
}
