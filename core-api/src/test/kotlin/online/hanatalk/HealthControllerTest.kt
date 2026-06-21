package online.hanatalk

import online.hanatalk.api.HealthController
import online.hanatalk.security.JwtService
import online.hanatalk.security.SecurityConfig
import online.hanatalk.security.UserDetailsServiceImpl
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(HealthController::class, excludeAutoConfiguration = [UserDetailsServiceAutoConfiguration::class])
@Import(SecurityConfig::class)
class HealthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var jwtService: JwtService

    @MockitoBean
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
