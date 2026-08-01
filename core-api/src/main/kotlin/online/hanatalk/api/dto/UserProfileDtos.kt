package online.hanatalk.api.dto

import online.hanatalk.domain.JlptLevel
import online.hanatalk.domain.Language
import online.hanatalk.domain.user.UserRole

data class UserProfileResponse(
    val username: String,
    val nativeLanguage: Language,
    val startingLevel: JlptLevel?,
    val role: UserRole,
)

data class SetLevelRequest(
    val level: JlptLevel,
)
