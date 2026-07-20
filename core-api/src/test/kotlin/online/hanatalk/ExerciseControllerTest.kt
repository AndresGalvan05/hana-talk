package online.hanatalk

import online.hanatalk.api.ExerciseController
import online.hanatalk.api.dto.AttemptResponse
import online.hanatalk.api.dto.ExerciseResponse
import online.hanatalk.domain.exercise.ExerciseType
import online.hanatalk.domain.user.User
import online.hanatalk.domain.user.UserRepository
import online.hanatalk.security.JwtService
import online.hanatalk.security.SecurityConfig
import online.hanatalk.security.UserDetailsServiceImpl
import online.hanatalk.service.ExerciseService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
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
import org.springframework.test.web.servlet.post
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@WebMvcTest(ExerciseController::class, excludeAutoConfiguration = [UserDetailsServiceAutoConfiguration::class])
@Import(SecurityConfig::class)
class ExerciseControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var exerciseService: ExerciseService

    @MockitoBean
    lateinit var userRepository: UserRepository

    @MockitoBean
    lateinit var jwtService: JwtService

    @MockitoBean
    lateinit var userDetailsService: UserDetailsServiceImpl

    private val lessonId = UUID.randomUUID()
    private val exerciseId = UUID.randomUUID()
    private val testUser = User(email = "user", username = "user", passwordHash = "hash")

    @Test
    fun `list exercises requires auth`() {
        mockMvc.get("/api/lessons/$lessonId/exercises")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    @WithMockUser
    fun `list exercises omits the correct answer`() {
        given(exerciseService.listByLesson(lessonId)).willReturn(
            listOf(
                ExerciseResponse(
                    id = exerciseId,
                    lessonId = lessonId,
                    type = ExerciseType.MCQ,
                    prompt = "What does X mean?",
                    options = listOf("A", "B"),
                ),
            ),
        )

        mockMvc.get("/api/lessons/$lessonId/exercises")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value(exerciseId.toString()) }
                jsonPath("$[0].prompt") { value("What does X mean?") }
                jsonPath("$[0].correctAnswer") { doesNotExist() }
            }
    }

    @Test
    fun `submit attempt requires auth`() {
        mockMvc.post("/api/exercises/$exerciseId/attempts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"answer":"foo"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    @WithMockUser
    fun `correct attempt returns correct true`() {
        given(userRepository.findByEmail("user")).willReturn(testUser)
        given(exerciseService.submitAttempt(eq(testUser.id), eq(exerciseId), any())).willReturn(AttemptResponse(correct = true))

        mockMvc.post("/api/exercises/$exerciseId/attempts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"answer":"Thank you"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.correct") { value(true) }
        }
    }

    @Test
    @WithMockUser
    fun `incorrect attempt returns correct false`() {
        given(userRepository.findByEmail("user")).willReturn(testUser)
        given(exerciseService.submitAttempt(eq(testUser.id), eq(exerciseId), any())).willReturn(AttemptResponse(correct = false))

        mockMvc.post("/api/exercises/$exerciseId/attempts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"answer":"wrong"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.correct") { value(false) }
        }
    }

    @Test
    @WithMockUser
    fun `attempt on a nonexistent exercise returns 404`() {
        given(userRepository.findByEmail("user")).willReturn(testUser)
        given(exerciseService.submitAttempt(eq(testUser.id), eq(exerciseId), any()))
            .willThrow(ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Exercise not found"))

        mockMvc.post("/api/exercises/$exerciseId/attempts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"answer":"anything"}"""
        }.andExpect { status { isNotFound() } }
    }
}
