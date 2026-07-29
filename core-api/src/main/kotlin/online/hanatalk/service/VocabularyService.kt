package online.hanatalk.service

import online.hanatalk.api.dto.VocabularyItemResponse
import online.hanatalk.api.dto.toResponse
import online.hanatalk.domain.vocabulary.VocabularyItemRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class VocabularyService(
    private val vocabularyItemRepository: VocabularyItemRepository,
) {
    fun listByLesson(lessonId: UUID): List<VocabularyItemResponse> =
        vocabularyItemRepository.findByLessonIdOrderByPosition(lessonId).map { it.toResponse() }
}
