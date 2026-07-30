package online.hanatalk.api

import online.hanatalk.api.dto.ConversationReplyRequest
import online.hanatalk.client.AiExerciseSvcClient
import online.hanatalk.client.ChatReplyDto
import online.hanatalk.domain.JlptLevel
import online.hanatalk.domain.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/conversation")
class ConversationController(
    private val aiExerciseSvcClient: AiExerciseSvcClient,
    private val userRepository: UserRepository,
) {
    @PostMapping("/reply")
    fun reply(
        @AuthenticationPrincipal principal: UserDetails,
        @RequestBody request: ConversationReplyRequest,
    ): ChatReplyDto {
        val user =
            userRepository.findByEmail(principal.username)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        val jlptLevel = user.startingLevel ?: JlptLevel.N5
        return aiExerciseSvcClient.getChatReply(jlptLevel.name, request.history, request.message)
    }
}
