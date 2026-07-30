package online.hanatalk.domain.vocabulary

import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.UUID

@Embeddable
data class UserVocabularyProgressId(
    val userId: UUID = UUID.randomUUID(),
    val vocabularyItemId: UUID = UUID.randomUUID(),
) : Serializable
