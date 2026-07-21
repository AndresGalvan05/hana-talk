package online.hanatalk.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Component
class AiExerciseSvcClient(
    restClientBuilder: RestClient.Builder,
    @Value("\${ai-exercise-svc.url}") baseUrl: String,
    @Value("\${ai-exercise-svc.timeout-seconds:15}") timeoutSeconds: Long,
) {
    // Must use the Spring-autoconfigured RestClient.Builder, not the static
    // RestClient.builder() factory — per Spring Boot's own docs, the static
    // factory bypasses all auto-configuration, including the
    // ObservationRestClientCustomizer that injects trace-context headers.
    private val restClient: RestClient =
        restClientBuilder
            .baseUrl(baseUrl)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    val timeoutMillis = (timeoutSeconds * 1000).toInt()
                    setConnectTimeout(timeoutMillis)
                    setReadTimeout(timeoutMillis)
                },
            )
            .build()

    // Synchronous, on the user-facing request path: a failure here is a
    // real 5xx to the caller, not a best-effort side effect like Kafka
    // publishing — there's no sensible "list exercises" fallback.
    fun generateExercises(
        lessonId: UUID,
        content: String,
        jlptLevel: String,
    ): GenerationResultDto =
        try {
            restClient
                .post()
                .uri("/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(GenerateExercisesRequest(lessonId.toString(), content, jlptLevel))
                .retrieve()
                .body(GenerationResultDto::class.java)
                ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "ai-exercise-svc returned an empty response")
        } catch (e: RestClientException) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "ai-exercise-svc call failed", e)
        }
}
