package online.hanatalk.api

import online.hanatalk.api.dto.VocabularyItemResponse
import online.hanatalk.service.VocabularyService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class VocabularyController(
    private val vocabularyService: VocabularyService,
) {
    @GetMapping("/api/lessons/{lessonId}/vocabulary")
    fun listByLesson(
        @PathVariable lessonId: UUID,
    ): List<VocabularyItemResponse> = vocabularyService.listByLesson(lessonId)
}
