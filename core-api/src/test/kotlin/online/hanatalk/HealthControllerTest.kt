package online.hanatalk

import online.hanatalk.api.HealthController
import online.hanatalk.security.JwtService
import online.hanatalk.security.UserDetailsServiceImpl
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(HealthController::class)
class HealthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    // Required by JwtAuthFilter which is loaded with the security config
    @MockBean
    lateinit var jwtService: JwtService

    @MockBean
    lateinit var userDetailsService: UserDetailsServiceImpl

    @Test
    fun `health endpoint returns ok`() {
        mockMvc.get("/api/health")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("ok") }
            }
    }
}
