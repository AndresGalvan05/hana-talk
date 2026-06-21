package online.hanatalk.service

import online.hanatalk.api.dto.AuthResponse
import online.hanatalk.api.dto.LoginRequest
import online.hanatalk.api.dto.RegisterRequest
import online.hanatalk.domain.user.User
import online.hanatalk.domain.user.UserRepository
import online.hanatalk.security.JwtService
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
) {

    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByEmail(request.email))
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email already registered")
        if (userRepository.existsByUsername(request.username))
            throw ResponseStatusException(HttpStatus.CONFLICT, "Username already taken")

        val user = User(
            email = request.email,
            username = request.username,
            passwordHash = passwordEncoder.encode(request.password),
        )
        userRepository.save(user)

        return AuthResponse(token = jwtService.generate(user.email), username = user.username)
    }

    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")

        if (!passwordEncoder.matches(request.password, user.passwordHash))
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")

        return AuthResponse(token = jwtService.generate(user.email), username = user.username)
    }
}
