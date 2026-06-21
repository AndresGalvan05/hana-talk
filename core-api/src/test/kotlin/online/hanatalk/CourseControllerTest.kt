package online.hanatalk

import online.hanatalk.api.CourseController
import online.hanatalk.api.dto.CourseResponse
import online.hanatalk.domain.Language
import online.hanatalk.security.JwtService
import online.hanatalk.security.SecurityConfig
import online.hanatalk.security.UserDetailsServiceImpl
import online.hanatalk.service.CourseService
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

@WebMvcTest(CourseController::class, excludeAutoConfiguration = [UserDetailsServiceAutoConfiguration::class])
@Import(SecurityConfig::class)
class CourseControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var courseService: CourseService

    @MockitoBean
    lateinit var jwtService: JwtService

    @MockitoBean
    lateinit var userDetailsService: UserDetailsServiceImpl

    private val sampleCourse = CourseResponse(
        id = UUID.randomUUID(),
        title = "Español Básico",
        language = Language.SPANISH,
        description = null,
        createdAt = Instant.now(),
    )

    @Test
    fun `list courses is public and returns 200`() {
        given(courseService.listAll(null)).willReturn(listOf(sampleCourse))

        mockMvc.get("/api/courses")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].title") { value("Español Básico") }
            }
    }

    @Test
    fun `list courses filtered by language`() {
        given(courseService.listAll(Language.SPANISH)).willReturn(listOf(sampleCourse))

        mockMvc.get("/api/courses?language=SPANISH")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].language") { value("SPANISH") }
            }
    }

    @Test
    fun `get course by id is public`() {
        given(courseService.get(sampleCourse.id)).willReturn(sampleCourse)

        mockMvc.get("/api/courses/${sampleCourse.id}")
            .andExpect {
                status { isOk() }
                jsonPath("$.title") { value("Español Básico") }
            }
    }

    @Test
    fun `create course without auth returns 401`() {
        mockMvc.post("/api/courses") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Test","language":"SPANISH"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    @WithMockUser
    fun `create course with auth returns 201`() {
        given(courseService.create(any())).willReturn(sampleCourse)

        mockMvc.post("/api/courses") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Español Básico","language":"SPANISH"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.language") { value("SPANISH") }
        }
    }

    @Test
    @WithMockUser
    fun `update course returns 200`() {
        given(courseService.update(any(), any())).willReturn(sampleCourse)

        mockMvc.put("/api/courses/${sampleCourse.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Updated","language":"SPANISH"}"""
        }.andExpect { status { isOk() } }
    }

    @Test
    @WithMockUser
    fun `delete course returns 204`() {
        mockMvc.delete("/api/courses/${sampleCourse.id}")
            .andExpect { status { isNoContent() } }
    }
}
