package online.hanatalk

import online.hanatalk.api.HealthController
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(HealthController::class)
class HealthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `health endpoint returns ok`() {
        mockMvc.get("/api/health")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("ok") }
            }
    }
}
