package online.hanatalk.service

import online.hanatalk.api.dto.UserProfileResponse
import online.hanatalk.domain.JlptLevel
import online.hanatalk.domain.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class UserProfileService(private val userRepository: UserRepository) {
    fun getProfile(email: String): UserProfileResponse {
        val user = userRepository.findByEmail(email) ?: throw notFound()
        return UserProfileResponse(user.username, user.nativeLanguage, user.startingLevel, user.role)
    }

    fun setLevel(
        email: String,
        level: JlptLevel,
    ): UserProfileResponse {
        val user = userRepository.findByEmail(email) ?: throw notFound()
        user.startingLevel = level
        userRepository.save(user)
        return UserProfileResponse(user.username, user.nativeLanguage, user.startingLevel, user.role)
    }

    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
}
