package online.hanatalk.security

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class RateLimiter(private val clock: Clock) {
    private class Window(
        var count: Int,
        var startedAt: Instant,
    )

    private val windows = ConcurrentHashMap<String, Window>()

    fun tryAcquire(
        key: String,
        maxRequests: Int,
        window: Duration,
    ): Boolean {
        val state = windows.computeIfAbsent(key) { Window(0, clock.instant()) }
        synchronized(state) {
            val now = clock.instant()
            if (Duration.between(state.startedAt, now) >= window) {
                state.count = 0
                state.startedAt = now
            }
            if (state.count >= maxRequests) return false
            state.count++
            return true
        }
    }
}
