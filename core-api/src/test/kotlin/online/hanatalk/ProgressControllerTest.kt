package online.hanatalk

import online.hanatalk.api.ProgressController
import online.hanatalk.api.dto.CourseProgressResponse
import online.hanatalk.domain.user.User
import online.hanatalk.domain.user.UserRepository
import online.hanatalk.security.JwtService
import online.hanatalk.security.SecurityConfig
import online.hanatalk.security.UserDetailsServiceImpl
import online.hanatalk.service.ProgressService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

@WebMvcTest(ProgressController::class, excludeAutoConfiguration = [UserDetailsServiceAutoConfiguration::class])
@Import(SecurityConfig::class)
class ProgressControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var progressService: ProgressService

    @MockitoBean
    lateinit var userRepository: UserRepository

    @MockitoBean
    lateinit var jwtService: JwtService

    @MockitoBean
    lateinit var userDetailsService: UserDetailsServiceImpl

    private val courseId = UUID.randomUUID()
    private val lessonId = UUID.randomUUID()
    private val testUser = User(email = "user", username = "user", passwordHash = "hash")

    @Test
    fun `mark complete requires auth`() {
        mockMvc.post("/api/courses/$courseId/lessons/$lessonId/complete")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    @WithMockUser
    fun `mark complete returns 204`() {
        given(userRepository.findByEmail("user")).willReturn(testUser)

        mockMvc.post("/api/courses/$courseId/lessons/$lessonId/complete")
            .andExpect { status { isNoContent() } }
    }

    @Test
    @WithMockUser
    fun `mark complete is idempotent`() {
        given(userRepository.findByEmail("user")).willReturn(testUser)

        mockMvc.post("/api/courses/$courseId/lessons/$lessonId/complete")
            .andExpect { status { isNoContent() } }
        mockMvc.post("/api/courses/$courseId/lessons/$lessonId/complete")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `get course progress requires auth`() {
        mockMvc.get("/api/courses/$courseId/progress")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    @WithMockUser
    fun `get course progress returns counts`() {
        given(userRepository.findByEmail("user")).willReturn(testUser)
        given(progressService.getCourseProgress(any(), any())).willReturn(
            CourseProgressResponse(completed = 3, total = 10),
        )

        mockMvc.get("/api/courses/$courseId/progress")
            .andExpect {
                status { isOk() }
                jsonPath("$.completed") { value(3) }
                jsonPath("$.total") { value(10) }
            }
    }
}
