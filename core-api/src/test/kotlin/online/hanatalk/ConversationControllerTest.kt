package online.hanatalk

import online.hanatalk.api.ConversationController
import online.hanatalk.client.AiExerciseSvcClient
import online.hanatalk.client.ChatReplyDto
import online.hanatalk.domain.JlptLevel
import online.hanatalk.domain.user.User
import online.hanatalk.domain.user.UserRepository
import online.hanatalk.security.JwtService
import online.hanatalk.security.RateLimiter
import online.hanatalk.security.SecurityConfig
import online.hanatalk.security.UserDetailsServiceImpl
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.given
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.web.server.ResponseStatusException

@WebMvcTest(ConversationController::class, excludeAutoConfiguration = [UserDetailsServiceAutoConfiguration::class])
@Import(SecurityConfig::class)
class ConversationControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var aiExerciseSvcClient: AiExerciseSvcClient

    @MockitoBean
    lateinit var userRepository: UserRepository

    @MockitoBean
    lateinit var jwtService: JwtService

    @MockitoBean
    lateinit var userDetailsService: UserDetailsServiceImpl

    @MockitoBean
    lateinit var rateLimiter: RateLimiter

    private val requestBody =
        """{"history":[{"speaker":"tutor","japanese":"こんにちは！"}],"message":"こんにちは。"}"""

    private val sampleReply = ChatReplyDto(japanese = "げんきですか。", english = "Are you doing well?", correction = null)

    @Test
    fun `reply requires auth`() {
        mockMvc.post("/api/conversation/reply") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    @WithMockUser(username = "taro@example.com")
    fun `reply derives N5 for a user with no stored level`() {
        val userWithNoLevel = User(email = "taro@example.com", username = "taro", passwordHash = "hash")
        given(userRepository.findByEmail("taro@example.com")).willReturn(userWithNoLevel)
        given(rateLimiter.tryAcquire(any(), any(), any())).willReturn(true)
        given(aiExerciseSvcClient.getChatReply(eq("N5"), any(), any())).willReturn(sampleReply)

        mockMvc.post("/api/conversation/reply") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isOk() }
            jsonPath("$.japanese") { value("げんきですか。") }
        }
    }

    @Test
    @WithMockUser(username = "taro@example.com")
    fun `reply uses the caller's stored JLPT level`() {
        val userWithLevel =
            User(email = "taro@example.com", username = "taro", passwordHash = "hash").apply {
                startingLevel = JlptLevel.N3
            }
        given(userRepository.findByEmail("taro@example.com")).willReturn(userWithLevel)
        given(rateLimiter.tryAcquire(any(), any(), any())).willReturn(true)
        given(aiExerciseSvcClient.getChatReply(eq("N3"), any(), any())).willReturn(sampleReply)

        mockMvc.post("/api/conversation/reply") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect { status { isOk() } }
    }

    @Test
    @WithMockUser(username = "taro@example.com")
    fun `reply propagates a downstream ai-exercise-svc failure as the same status`() {
        val user = User(email = "taro@example.com", username = "taro", passwordHash = "hash")
        given(userRepository.findByEmail("taro@example.com")).willReturn(user)
        given(rateLimiter.tryAcquire(any(), any(), any())).willReturn(true)
        given(aiExerciseSvcClient.getChatReply(any(), any(), any()))
            .willThrow(ResponseStatusException(HttpStatus.BAD_GATEWAY, "ai-exercise-svc call failed"))

        mockMvc.post("/api/conversation/reply") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect { status { isBadGateway() } }
    }

    @Test
    @WithMockUser(username = "taro@example.com")
    fun `reply is rejected with 429 once the rate limit is exceeded, without calling ai-exercise-svc`() {
        val user = User(email = "taro@example.com", username = "taro", passwordHash = "hash")
        given(userRepository.findByEmail("taro@example.com")).willReturn(user)
        given(rateLimiter.tryAcquire(any(), any(), any())).willReturn(false)

        mockMvc.post("/api/conversation/reply") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect { status { isEqualTo(429) } }

        verify(aiExerciseSvcClient, never()).getChatReply(any(), any(), any())
    }

    @Test
    @WithMockUser(username = "ghost@example.com")
    fun `reply 404s if the authenticated user is somehow not found`() {
        given(userRepository.findByEmail("ghost@example.com")).willReturn(null)

        mockMvc.post("/api/conversation/reply") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect { status { isNotFound() } }
    }
}
