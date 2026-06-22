package online.hanatalk.domain.progress

import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.UUID

@Embeddable
data class UserLessonProgressId(
    val userId: UUID = UUID.randomUUID(),
    val lessonId: UUID = UUID.randomUUID(),
) : Serializable
