package online.hanatalk.api

import jakarta.validation.Valid
import online.hanatalk.api.dto.CourseRequest
import online.hanatalk.api.dto.CourseResponse
import online.hanatalk.domain.Language
import online.hanatalk.service.CourseService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/courses")
class CourseController(private val courseService: CourseService) {

    @GetMapping
    fun list(@RequestParam(required = false) language: Language?): List<CourseResponse> =
        courseService.listAll(language)

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): CourseResponse =
        courseService.get(id)

    @PostMapping
    fun create(@RequestBody @Valid request: CourseRequest): ResponseEntity<CourseResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(courseService.create(request))

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody @Valid request: CourseRequest): CourseResponse =
        courseService.update(id, request)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        courseService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
