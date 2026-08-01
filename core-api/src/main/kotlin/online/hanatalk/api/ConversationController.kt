package online.hanatalk.api

import online.hanatalk.api.dto.ConversationReplyRequest
import online.hanatalk.client.AiExerciseSvcClient
import online.hanatalk.client.ChatReplyDto
import online.hanatalk.domain.JlptLevel
import online.hanatalk.domain.user.UserRepository
import online.hanatalk.security.RateLimiter
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Duration

private const val CHAT_MAX_REQUESTS_PER_MINUTE = 10

@RestController
@RequestMapping("/api/conversation")
class ConversationController(
    private val aiExerciseSvcClient: AiExerciseSvcClient,
    private val userRepository: UserRepository,
    private val rateLimiter: RateLimiter,
) {
    @PostMapping("/reply")
    fun reply(
        @AuthenticationPrincipal principal: UserDetails,
        @RequestBody request: ConversationReplyRequest,
    ): ChatReplyDto {
        val user =
            userRepository.findByEmail(principal.username)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        val allowed =
            rateLimiter.tryAcquire("chat:${user.id}", CHAT_MAX_REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
        if (!allowed) {
            throw ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many messages -- please wait a moment and try again.",
            )
        }

        val jlptLevel = user.startingLevel ?: JlptLevel.N5
        return aiExerciseSvcClient.getChatReply(jlptLevel.name, request.history, request.message)
    }
}
