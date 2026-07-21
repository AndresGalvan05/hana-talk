package online.hanatalk.api

import online.hanatalk.api.dto.SetLevelRequest
import online.hanatalk.api.dto.UserProfileResponse
import online.hanatalk.client.EventWorkerClient
import online.hanatalk.client.StreakResponse
import online.hanatalk.domain.user.UserRepository
import online.hanatalk.service.UserProfileService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/users")
class UserProfileController(
    private val userProfileService: UserProfileService,
    private val eventWorkerClient: EventWorkerClient,
    private val userRepository: UserRepository,
) {
    @GetMapping("/me")
    fun getProfile(
        @AuthenticationPrincipal principal: UserDetails,
    ): UserProfileResponse = userProfileService.getProfile(principal.username)

    @PatchMapping("/me/level")
    fun setLevel(
        @AuthenticationPrincipal principal: UserDetails,
        @RequestBody request: SetLevelRequest,
    ): UserProfileResponse = userProfileService.setLevel(principal.username, request.level)

    @GetMapping("/me/streak")
    fun getStreak(
        @AuthenticationPrincipal principal: UserDetails,
    ): StreakResponse {
        val user =
            userRepository.findByEmail(principal.username)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        return eventWorkerClient.getStreak(user.id)
    }
}
