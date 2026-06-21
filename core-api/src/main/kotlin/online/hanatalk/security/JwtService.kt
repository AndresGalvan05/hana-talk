package online.hanatalk.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.Date

@Service
class JwtService(@Value("\${jwt.secret}") secret: String) {

    private val key = Keys.hmacShaKeyFor(secret.toByteArray())
    private val ttl = Duration.ofHours(1)

    fun generate(subject: String): String = Jwts.builder()
        .subject(subject)
        .issuedAt(Date())
        .expiration(Date.from(Instant.now().plus(ttl)))
        .signWith(key)
        .compact()

    fun extractSubject(token: String): String = claims(token).subject

    fun isValid(token: String): Boolean = runCatching {
        claims(token).expiration.after(Date())
    }.getOrDefault(false)

    private fun claims(token: String) = Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .payload
}
