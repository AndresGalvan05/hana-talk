package online.hanatalk

import online.hanatalk.api.VocabularyReviewController
import online.hanatalk.api.dto.VocabularyItemResponse
import online.hanatalk.api.dto.VocabularyReviewResponse
import online.hanatalk.domain.user.User
import online.hanatalk.domain.user.UserRepository
import online.hanatalk.security.JwtService
import online.hanatalk.security.SecurityConfig
import online.hanatalk.security.UserDetailsServiceImpl
import online.hanatalk.service.VocabularyReviewService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@WebMvcTest(VocabularyReviewController::class, excludeAutoConfiguration = [UserDetailsServiceAutoConfiguration::class])
@Import(SecurityConfig::class)
class VocabularyReviewControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var vocabularyReviewService: VocabularyReviewService

    @MockitoBean
    lateinit var userRepository: UserRepository

    @MockitoBean
    lateinit var jwtService: JwtService

    @MockitoBean
    lateinit var userDetailsService: UserDetailsServiceImpl

    private val testUser = User(email = "taro@example.com", username = "taro", passwordHash = "hash")
    private val itemId = UUID.randomUUID()

    @Test
    fun `get review queue requires auth`() {
        mockMvc.get("/api/vocabulary/review")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    @WithMockUser(username = "taro@example.com")
    fun `get review queue returns due items`() {
        given(userRepository.findByEmail("taro@example.com")).willReturn(testUser)
        given(vocabularyReviewService.getDueQueue(testUser.id))
            .willReturn(listOf(VocabularyItemResponse(itemId, "が", "ga", "subject marker")))

        mockMvc.get("/api/vocabulary/review")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].japanese") { value("が") }
            }
    }

    @Test
    fun `submit review requires auth`() {
        mockMvc.post("/api/vocabulary-items/$itemId/review") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"correct":true}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    @WithMockUser(username = "taro@example.com")
    fun `submit review returns the new schedule`() {
        given(userRepository.findByEmail("taro@example.com")).willReturn(testUser)
        given(vocabularyReviewService.submitReview(testUser.id, itemId, true))
            .willReturn(VocabularyReviewResponse(Instant.now(), 1, 1))

        mockMvc.post("/api/vocabulary-items/$itemId/review") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"correct":true}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.intervalDays") { value(1) }
        }
    }

    @Test
    @WithMockUser(username = "taro@example.com")
    fun `submit review for a nonexistent vocabulary item returns 404`() {
        given(userRepository.findByEmail("taro@example.com")).willReturn(testUser)
        given(vocabularyReviewService.submitReview(any(), any(), any()))
            .willThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Vocabulary item not found"))

        mockMvc.post("/api/vocabulary-items/$itemId/review") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"correct":true}"""
        }.andExpect { status { isNotFound() } }
    }
}
