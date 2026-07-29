package online.hanatalk.api.dto

import online.hanatalk.domain.vocabulary.VocabularyItem
import java.util.UUID

data class VocabularyItemResponse(
    val id: UUID,
    val japanese: String,
    val reading: String,
    val meaning: String,
)

fun VocabularyItem.toResponse() = VocabularyItemResponse(id, japanese, reading, meaning)
