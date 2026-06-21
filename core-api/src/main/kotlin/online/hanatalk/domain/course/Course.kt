package online.hanatalk.domain.course

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import online.hanatalk.domain.Language
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "courses")
class Course(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var title: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var language: Language,
    @Column
    var description: String? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
