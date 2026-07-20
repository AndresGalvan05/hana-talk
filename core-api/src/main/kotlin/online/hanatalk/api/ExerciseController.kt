package online.hanatalk.api

import jakarta.validation.Valid
import online.hanatalk.api.dto.AttemptRequest
import online.hanatalk.api.dto.AttemptResponse
import online.hanatalk.api.dto.ExerciseResponse
import online.hanatalk.domain.user.UserRepository
import online.hanatalk.service.ExerciseService
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
class ExerciseController(
    private val exerciseService: ExerciseService,
    private val userRepository: UserRepository,
) {
    @GetMapping("/api/lessons/{lessonId}/exercises")
    fun listByLesson(
        @PathVariable lessonId: UUID,
    ): List<ExerciseResponse> = exerciseService.listByLesson(lessonId)

    @PostMapping("/api/exercises/{exerciseId}/attempts")
    fun submitAttempt(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable exerciseId: UUID,
        @RequestBody @Valid request: AttemptRequest,
    ): AttemptResponse {
        val user =
            userRepository.findByEmail(principal.username)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        return exerciseService.submitAttempt(user.id, exerciseId, request.answer)
    }
}
