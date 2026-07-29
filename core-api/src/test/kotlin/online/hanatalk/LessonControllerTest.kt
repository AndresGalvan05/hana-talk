package online.hanatalk

import online.hanatalk.api.LessonController
import online.hanatalk.api.dto.CultureNote
import online.hanatalk.api.dto.Dialogue
import online.hanatalk.api.dto.LessonContent
import online.hanatalk.api.dto.LessonResponse
import online.hanatalk.security.JwtService
import online.hanatalk.security.SecurityConfig
import online.hanatalk.security.UserDetailsServiceImpl
import online.hanatalk.service.LessonService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Instant
import java.util.UUID

@WebMvcTest(LessonController::class, excludeAutoConfiguration = [UserDetailsServiceAutoConfiguration::class])
@Import(SecurityConfig::class)
class LessonControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var lessonService: LessonService

    @MockitoBean
    lateinit var jwtService: JwtService

    @MockitoBean
    lateinit var userDetailsService: UserDetailsServiceImpl

    private val courseId = UUID.randomUUID()

    private val sampleContent =
        LessonContent(
            grammarPoints = emptyList(),
            dialogue = Dialogue(title = "t", lines = emptyList()),
            cultureNote = CultureNote(title = "t", body = "b"),
        )

    private val sampleLesson =
        LessonResponse(
            id = UUID.randomUUID(),
            courseId = courseId,
            title = "Saludos",
            content = sampleContent,
            position = 1,
            createdAt = Instant.now(),
        )

    private val sampleContentJson =
        """{"grammarPoints":[],"dialogue":{"title":"t","lines":[]},"cultureNote":{"title":"t","body":"b"}}"""

    @Test
    fun `list lessons is public`() {
        given(lessonService.listForCourse(courseId)).willReturn(listOf(sampleLesson))

        mockMvc.get("/api/courses/$courseId/lessons")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].title") { value("Saludos") }
            }
    }

    @Test
    fun `get lesson is public`() {
        given(lessonService.get(courseId, sampleLesson.id)).willReturn(sampleLesson)

        mockMvc.get("/api/courses/$courseId/lessons/${sampleLesson.id}")
            .andExpect {
                status { isOk() }
                jsonPath("$.position") { value(1) }
            }
    }

    @Test
    fun `create lesson without auth returns 401`() {
        mockMvc.post("/api/courses/$courseId/lessons") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Saludos","content":$sampleContentJson,"position":1}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `create lesson as admin returns 201`() {
        given(lessonService.create(any(), any())).willReturn(sampleLesson)

        mockMvc.post("/api/courses/$courseId/lessons") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Saludos","content":$sampleContentJson,"position":1}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.title") { value("Saludos") }
        }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `update lesson as admin returns 200`() {
        given(lessonService.update(any(), any(), any())).willReturn(sampleLesson)

        mockMvc.put("/api/courses/$courseId/lessons/${sampleLesson.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Updated","content":$sampleContentJson,"position":2}"""
        }.andExpect { status { isOk() } }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `delete lesson as admin returns 204`() {
        mockMvc.delete("/api/courses/$courseId/lessons/${sampleLesson.id}")
            .andExpect { status { isNoContent() } }
    }

    @Test
    @WithMockUser
    fun `create lesson as non-admin returns 403`() {
        mockMvc.post("/api/courses/$courseId/lessons") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Saludos","content":$sampleContentJson,"position":1}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    @WithMockUser
    fun `update lesson as non-admin returns 403`() {
        mockMvc.put("/api/courses/$courseId/lessons/${sampleLesson.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Updated","content":$sampleContentJson,"position":2}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    @WithMockUser
    fun `delete lesson as non-admin returns 403`() {
        mockMvc.delete("/api/courses/$courseId/lessons/${sampleLesson.id}")
            .andExpect { status { isForbidden() } }
    }
}
