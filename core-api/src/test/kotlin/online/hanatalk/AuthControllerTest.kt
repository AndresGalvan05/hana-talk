package online.hanatalk

import online.hanatalk.api.AuthController
import online.hanatalk.api.dto.AuthResponse
import online.hanatalk.security.JwtService
import online.hanatalk.security.SecurityConfig
import online.hanatalk.security.UserDetailsServiceImpl
import online.hanatalk.service.AuthService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(AuthController::class, excludeAutoConfiguration = [UserDetailsServiceAutoConfiguration::class])
@Import(SecurityConfig::class)
class AuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var authService: AuthService

    @MockitoBean
    lateinit var jwtService: JwtService

    @MockitoBean
    lateinit var userDetailsService: UserDetailsServiceImpl

    @Test
    fun `register returns 201 with token and username`() {
        given(authService.register(any())).willReturn(AuthResponse("tok", "alice"))

        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"alice@example.com","username":"alice","password":"secret123"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.token") { value("tok") }
            jsonPath("$.username") { value("alice") }
        }
    }

    @Test
    fun `login returns 200 with token`() {
        given(authService.login(any())).willReturn(AuthResponse("tok", "alice"))

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"alice@example.com","password":"secret123"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.token") { value("tok") }
        }
    }
}
