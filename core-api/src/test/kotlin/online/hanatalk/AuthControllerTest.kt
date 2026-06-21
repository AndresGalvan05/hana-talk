package online.hanatalk

import online.hanatalk.api.dto.AuthResponse
import online.hanatalk.security.JwtService
import online.hanatalk.security.UserDetailsServiceImpl
import online.hanatalk.service.AuthService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import online.hanatalk.api.AuthController

@WebMvcTest(AuthController::class)
class AuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockBean
    lateinit var authService: AuthService

    @MockBean
    lateinit var jwtService: JwtService

    @MockBean
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
