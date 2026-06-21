package online.hanatalk.api

import jakarta.validation.Valid
import online.hanatalk.api.dto.LessonRequest
import online.hanatalk.api.dto.LessonResponse
import online.hanatalk.service.LessonService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/courses/{courseId}/lessons")
class LessonController(private val lessonService: LessonService) {
    @GetMapping
    fun list(
        @PathVariable courseId: UUID,
    ): List<LessonResponse> = lessonService.listForCourse(courseId)

    @GetMapping("/{id}")
    fun get(
        @PathVariable courseId: UUID,
        @PathVariable id: UUID,
    ): LessonResponse = lessonService.get(courseId, id)

    @PostMapping
    fun create(
        @PathVariable courseId: UUID,
        @RequestBody @Valid request: LessonRequest,
    ): ResponseEntity<LessonResponse> = ResponseEntity.status(HttpStatus.CREATED).body(lessonService.create(courseId, request))

    @PutMapping("/{id}")
    fun update(
        @PathVariable courseId: UUID,
        @PathVariable id: UUID,
        @RequestBody @Valid request: LessonRequest,
    ): LessonResponse = lessonService.update(courseId, id, request)

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable courseId: UUID,
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        lessonService.delete(courseId, id)
        return ResponseEntity.noContent().build()
    }
}
