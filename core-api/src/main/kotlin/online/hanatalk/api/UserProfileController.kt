package online.hanatalk.api

import online.hanatalk.api.dto.SetLevelRequest
import online.hanatalk.api.dto.UserProfileResponse
import online.hanatalk.service.UserProfileService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
class UserProfileController(private val userProfileService: UserProfileService) {
    @GetMapping("/me")
    fun getProfile(
        @AuthenticationPrincipal principal: UserDetails,
    ): UserProfileResponse = userProfileService.getProfile(principal.username)

    @PatchMapping("/me/level")
    fun setLevel(
        @AuthenticationPrincipal principal: UserDetails,
        @RequestBody request: SetLevelRequest,
    ): UserProfileResponse = userProfileService.setLevel(principal.username, request.level)
}
