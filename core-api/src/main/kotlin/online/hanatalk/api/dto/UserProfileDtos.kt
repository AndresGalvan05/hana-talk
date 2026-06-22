package online.hanatalk.api.dto

import online.hanatalk.domain.JlptLevel
import online.hanatalk.domain.Language

data class UserProfileResponse(
    val username: String,
    val nativeLanguage: Language,
    val startingLevel: JlptLevel?,
)

data class SetLevelRequest(
    val level: JlptLevel,
)
