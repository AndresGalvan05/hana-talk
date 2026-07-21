package online.hanatalk

import online.hanatalk.api.UserProfileController
import online.hanatalk.api.dto.UserProfileResponse
import online.hanatalk.client.EventWorkerClient
import online.hanatalk.client.StreakResponse
import online.hanatalk.domain.JlptLevel
import online.hanatalk.domain.Language
import online.hanatalk.domain.user.User
import online.hanatalk.domain.user.UserRepository
import online.hanatalk.security.JwtService
import online.hanatalk.security.SecurityConfig
import online.hanatalk.security.UserDetailsServiceImpl
import online.hanatalk.service.UserProfileService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.web.server.ResponseStatusException

@WebMvcTest(UserProfileController::class, excludeAutoConfiguration = [UserDetailsServiceAutoConfiguration::class])
@Import(SecurityConfig::class)
class UserProfileControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var userProfileService: UserProfileService

    @MockitoBean
    lateinit var eventWorkerClient: EventWorkerClient

    @MockitoBean
    lateinit var userRepository: UserRepository

    @MockitoBean
    lateinit var jwtService: JwtService

    @MockitoBean
    lateinit var userDetailsService: UserDetailsServiceImpl

    private val sampleProfile =
        UserProfileResponse(
            username = "taro",
            nativeLanguage = Language.ENGLISH,
            startingLevel = JlptLevel.N5,
        )

    private val testUser = User(email = "taro@example.com", username = "taro", passwordHash = "hash")

    @Test
    fun `get profile requires auth`() {
        mockMvc.get("/api/users/me")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    @WithMockUser(username = "taro@example.com")
    fun `get profile returns user data`() {
        given(userProfileService.getProfile("taro@example.com")).willReturn(sampleProfile)

        mockMvc.get("/api/users/me")
            .andExpect {
                status { isOk() }
                jsonPath("$.username") { value("taro") }
                jsonPath("$.nativeLanguage") { value("ENGLISH") }
                jsonPath("$.startingLevel") { value("N5") }
            }
    }

    @Test
    fun `set level requires auth`() {
        mockMvc.patch("/api/users/me/level") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"level":"N4"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    @WithMockUser(username = "taro@example.com")
    fun `set level updates and returns profile`() {
        val updated = sampleProfile.copy(startingLevel = JlptLevel.N4)
        given(userProfileService.setLevel("taro@example.com", JlptLevel.N4)).willReturn(updated)

        mockMvc.patch("/api/users/me/level") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"level":"N4"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.startingLevel") { value("N4") }
        }
    }

    @Test
    fun `get streak requires auth`() {
        mockMvc.get("/api/users/me/streak")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    @WithMockUser(username = "taro@example.com")
    fun `get streak proxies through event-worker`() {
        given(userRepository.findByEmail("taro@example.com")).willReturn(testUser)
        given(eventWorkerClient.getStreak(testUser.id))
            .willReturn(StreakResponse(userId = testUser.id.toString(), currentStreak = 3, lastActiveDate = "2026-07-20"))

        mockMvc.get("/api/users/me/streak")
            .andExpect {
                status { isOk() }
                jsonPath("$.currentStreak") { value(3) }
                jsonPath("$.lastActiveDate") { value("2026-07-20") }
            }
    }

    @Test
    @WithMockUser(username = "taro@example.com")
    fun `get streak surfaces a downstream event-worker failure as a 5xx`() {
        given(userRepository.findByEmail("taro@example.com")).willReturn(testUser)
        given(eventWorkerClient.getStreak(testUser.id))
            .willThrow(ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "event-worker call failed"))

        mockMvc.get("/api/users/me/streak")
            .andExpect { status { isBadGateway() } }
    }
}
