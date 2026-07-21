package online.hanatalk.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Component
class EventWorkerClient(
    restClientBuilder: RestClient.Builder,
    @Value("\${event-worker.url}") baseUrl: String,
    @Value("\${event-worker.timeout-seconds:10}") timeoutSeconds: Long,
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

    fun getStreak(userId: UUID): StreakResponse =
        try {
            restClient
                .get()
                .uri("/users/{userId}/streak", userId)
                .retrieve()
                .body(StreakResponse::class.java)
                ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "event-worker returned an empty response")
        } catch (e: RestClientException) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "event-worker call failed", e)
        }

    fun getLeaderboard(): List<LeaderboardEntryDto> =
        try {
            restClient
                .get()
                .uri("/leaderboard")
                .retrieve()
                .body(object : ParameterizedTypeReference<List<LeaderboardEntryDto>>() {})
                ?: emptyList()
        } catch (e: RestClientException) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "event-worker call failed", e)
        }
}
