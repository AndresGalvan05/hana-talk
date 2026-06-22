package online.hanatalk.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class EventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(EventPublisher::class.java)

    fun publishUserRegistered(
        userId: UUID,
        username: String,
        nativeLanguage: String,
    ) {
        send(
            KafkaTopics.USER_REGISTERED,
            userId.toString(),
            mapOf(
                "userId" to userId.toString(),
                "username" to username,
                "nativeLanguage" to nativeLanguage,
                "occurredAt" to Instant.now().toString(),
            ),
        )
    }

    fun publishExerciseCompleted(
        userId: UUID,
        lessonId: UUID,
        courseId: UUID,
        jlptLevel: String,
        source: String,
    ) {
        send(
            KafkaTopics.EXERCISE_COMPLETED,
            userId.toString(),
            mapOf(
                "userId" to userId.toString(),
                "lessonId" to lessonId.toString(),
                "courseId" to courseId.toString(),
                "jlptLevel" to jlptLevel,
                "source" to source,
                "occurredAt" to Instant.now().toString(),
            ),
        )
    }

    private fun send(
        topic: String,
        key: String,
        payload: Map<String, String>,
    ) {
        val json = objectMapper.writeValueAsString(payload)
        kafkaTemplate.send(topic, key, json).whenComplete { _, ex ->
            if (ex != null) log.error("Failed to publish to {}: {}", topic, ex.message, ex)
        }
    }
}
