package online.hanatalk.client

data class StreakResponse(
    val userId: String,
    val currentStreak: Int,
    val lastActiveDate: String?,
)

data class LeaderboardEntryDto(
    val userId: String,
    val username: String,
    val currentStreak: Int,
)

data class AchievementDto(
    val code: String,
    val title: String,
    val description: String,
    val unlocked: Boolean,
    val unlockedAt: String?,
)
