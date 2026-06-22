package online.hanatalk.api.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import online.hanatalk.domain.Language

data class RegisterRequest(
    @field:Email val email: String,
    @field:NotBlank @field:Size(min = 3, max = 30) val username: String,
    @field:NotBlank @field:Size(min = 8) val password: String,
    val nativeLanguage: Language = Language.ENGLISH,
)

data class LoginRequest(
    @field:Email val email: String,
    @field:NotBlank val password: String,
)

data class AuthResponse(
    val token: String,
    val username: String,
)
