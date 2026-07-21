package online.hanatalk.domain.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import online.hanatalk.domain.JlptLevel
import online.hanatalk.domain.Language
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true)
    val email: String,
    @Column(nullable = false, unique = true)
    val username: String,
    @Column(name = "password_hash", nullable = false)
    val passwordHash: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "native_language", nullable = false, length = 10)
    var nativeLanguage: Language = Language.ENGLISH,
    @Enumerated(EnumType.STRING)
    @Column(name = "starting_level", length = 5)
    var startingLevel: JlptLevel? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var role: UserRole = UserRole.USER,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
