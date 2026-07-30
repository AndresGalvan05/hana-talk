package online.hanatalk.api

import online.hanatalk.api.dto.ReviewResultRequest
import online.hanatalk.api.dto.VocabularyItemResponse
import online.hanatalk.api.dto.VocabularyReviewResponse
import online.hanatalk.domain.user.UserRepository
import online.hanatalk.service.VocabularyReviewService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
class VocabularyReviewController(
    private val vocabularyReviewService: VocabularyReviewService,
    private val userRepository: UserRepository,
) {
    @GetMapping("/api/vocabulary/review")
    fun getDueQueue(
        @AuthenticationPrincipal principal: UserDetails,
    ): List<VocabularyItemResponse> = vocabularyReviewService.getDueQueue(currentUserId(principal))

    @PostMapping("/api/vocabulary-items/{id}/review")
    fun submitReview(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable id: UUID,
        @RequestBody request: ReviewResultRequest,
    ): VocabularyReviewResponse = vocabularyReviewService.submitReview(currentUserId(principal), id, request.correct)

    private fun currentUserId(principal: UserDetails): UUID =
        (
            userRepository.findByEmail(principal.username)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        ).id
}
