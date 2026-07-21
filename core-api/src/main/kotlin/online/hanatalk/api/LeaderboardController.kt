package online.hanatalk.api

import online.hanatalk.client.EventWorkerClient
import online.hanatalk.client.LeaderboardEntryDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class LeaderboardController(private val eventWorkerClient: EventWorkerClient) {
    @GetMapping("/api/leaderboard")
    fun getLeaderboard(): List<LeaderboardEntryDto> = eventWorkerClient.getLeaderboard()
}
