package online.hanatalk

import online.hanatalk.security.RateLimiter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

private class MutableClock(
    private var instant: Instant,
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)

    override fun instant(): Instant = instant

    fun advance(duration: Duration) {
        instant = instant.plus(duration)
    }
}

class RateLimiterTest {
    private val clock = MutableClock(Instant.parse("2026-07-31T00:00:00Z"))
    private val rateLimiter = RateLimiter(clock)
    private val window = Duration.ofMinutes(1)

    @Test
    fun `allows requests up to the limit within a window`() {
        repeat(10) {
            assertTrue(rateLimiter.tryAcquire("user-1", 10, window))
        }
    }

    @Test
    fun `denies the request after the limit is reached`() {
        repeat(10) { rateLimiter.tryAcquire("user-1", 10, window) }

        assertFalse(rateLimiter.tryAcquire("user-1", 10, window))
    }

    @Test
    fun `allows requests again once the window has elapsed`() {
        repeat(10) { rateLimiter.tryAcquire("user-1", 10, window) }
        assertFalse(rateLimiter.tryAcquire("user-1", 10, window))

        clock.advance(Duration.ofMinutes(1).plusSeconds(1))

        assertTrue(rateLimiter.tryAcquire("user-1", 10, window))
    }

    @Test
    fun `tracks different keys independently`() {
        repeat(10) { rateLimiter.tryAcquire("user-1", 10, window) }

        assertFalse(rateLimiter.tryAcquire("user-1", 10, window))
        assertTrue(rateLimiter.tryAcquire("user-2", 10, window))
    }
}
