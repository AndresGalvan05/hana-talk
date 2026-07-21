package online.hanatalk

import online.hanatalk.api.LeaderboardController
import online.hanatalk.client.EventWorkerClient
import online.hanatalk.client.LeaderboardEntryDto
import online.hanatalk.security.JwtService
import online.hanatalk.security.SecurityConfig
import online.hanatalk.security.UserDetailsServiceImpl
import org.junit.jupiter.api.Test
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.server.ResponseStatusException

@WebMvcTest(LeaderboardController::class, excludeAutoConfiguration = [UserDetailsServiceAutoConfiguration::class])
@Import(SecurityConfig::class)
class LeaderboardControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var eventWorkerClient: EventWorkerClient

    @MockitoBean
    lateinit var jwtService: JwtService

    @MockitoBean
    lateinit var userDetailsService: UserDetailsServiceImpl

    @Test
    fun `get leaderboard requires auth`() {
        mockMvc.get("/api/leaderboard")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    @WithMockUser
    fun `get leaderboard proxies through event-worker`() {
        given(eventWorkerClient.getLeaderboard()).willReturn(
            listOf(
                LeaderboardEntryDto(userId = "u1", username = "ana", currentStreak = 10),
                LeaderboardEntryDto(userId = "u2", username = "kenji", currentStreak = 3),
            ),
        )

        mockMvc.get("/api/leaderboard")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].username") { value("ana") }
                jsonPath("$[0].currentStreak") { value(10) }
            }
    }

    @Test
    @WithMockUser
    fun `get leaderboard surfaces a downstream event-worker failure as a 5xx`() {
        given(eventWorkerClient.getLeaderboard())
            .willThrow(ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "event-worker call failed"))

        mockMvc.get("/api/leaderboard")
            .andExpect { status { isBadGateway() } }
    }
}
