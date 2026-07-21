package online.hanatalk

import online.hanatalk.api.CourseController
import online.hanatalk.api.dto.CourseResponse
import online.hanatalk.domain.JlptLevel
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

    private val sampleCourse =
        CourseResponse(
            id = UUID.randomUUID(),
            title = "N5 Hiragana",
            jlptLevel = JlptLevel.N5,
            description = null,
            createdAt = Instant.now(),
        )

    @Test
    fun `list courses is public and returns 200`() {
        given(courseService.listAll(null)).willReturn(listOf(sampleCourse))

        mockMvc.get("/api/courses")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].title") { value("N5 Hiragana") }
            }
    }

    @Test
    fun `list courses filtered by jlpt level`() {
        given(courseService.listAll(JlptLevel.N5)).willReturn(listOf(sampleCourse))

        mockMvc.get("/api/courses?jlptLevel=N5")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].jlptLevel") { value("N5") }
            }
    }

    @Test
    fun `get course by id is public`() {
        given(courseService.get(sampleCourse.id)).willReturn(sampleCourse)

        mockMvc.get("/api/courses/${sampleCourse.id}")
            .andExpect {
                status { isOk() }
                jsonPath("$.title") { value("N5 Hiragana") }
            }
    }

    @Test
    fun `create course without auth returns 401`() {
        mockMvc.post("/api/courses") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"N5 Hiragana","jlptLevel":"N5"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `create course as admin returns 201`() {
        given(courseService.create(any())).willReturn(sampleCourse)

        mockMvc.post("/api/courses") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"N5 Hiragana","jlptLevel":"N5"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.jlptLevel") { value("N5") }
        }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `update course as admin returns 200`() {
        given(courseService.update(any(), any())).willReturn(sampleCourse)

        mockMvc.put("/api/courses/${sampleCourse.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Updated","jlptLevel":"N5"}"""
        }.andExpect { status { isOk() } }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `delete course as admin returns 204`() {
        mockMvc.delete("/api/courses/${sampleCourse.id}")
            .andExpect { status { isNoContent() } }
    }

    @Test
    @WithMockUser
    fun `create course as non-admin returns 403`() {
        mockMvc.post("/api/courses") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"N5 Hiragana","jlptLevel":"N5"}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    @WithMockUser
    fun `update course as non-admin returns 403`() {
        mockMvc.put("/api/courses/${sampleCourse.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Updated","jlptLevel":"N5"}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    @WithMockUser
    fun `delete course as non-admin returns 403`() {
        mockMvc.delete("/api/courses/${sampleCourse.id}")
            .andExpect { status { isForbidden() } }
    }
}
