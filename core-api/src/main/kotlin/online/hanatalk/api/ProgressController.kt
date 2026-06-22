package online.hanatalk.api

import online.hanatalk.api.dto.CourseProgressResponse
import online.hanatalk.domain.progress.CompletionSource
import online.hanatalk.domain.user.UserRepository
import online.hanatalk.service.ProgressService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/courses")
class ProgressController(
    private val progressService: ProgressService,
    private val userRepository: UserRepository,
) {
    @PostMapping("/{courseId}/lessons/{lessonId}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markComplete(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable courseId: UUID,
        @PathVariable lessonId: UUID,
    ) {
        val user =
            userRepository.findByEmail(principal.username)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        progressService.markComplete(user.id, lessonId, courseId, CompletionSource.MANUAL)
    }

    @GetMapping("/{courseId}/progress")
    fun getCourseProgress(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable courseId: UUID,
    ): CourseProgressResponse {
        val user =
            userRepository.findByEmail(principal.username)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        return progressService.getCourseProgress(user.id, courseId)
    }
}
