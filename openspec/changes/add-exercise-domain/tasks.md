# Tasks — add exercise domain (core-api, no LLM)

## 1. Domain model

- [x] 1.1 Add `CompletionSource.EXERCISE` to the existing enum
- [x] 1.2 Create `domain/exercise/ExerciseType.kt` (`MCQ`, `FILL_IN_BLANK`)
- [x] 1.3 Create `domain/exercise/Exercise.kt` entity: id, lessonId (FK),
      type, prompt, options (nullable JSON/text column), correctAnswer,
      createdAt — follow `Lesson.kt`'s entity style
- [x] 1.4 Create `domain/exercise/ExerciseAttempt.kt` entity: id, userId (FK),
      exerciseId (FK), submittedAnswer, isCorrect, attemptedAt
- [x] 1.5 Create `ExerciseRepository` (`findByLessonId`) and
      `ExerciseAttemptRepository` following `LessonRepository`'s style

## 2. Migration + seed

- [x] 2.1 Write `V8__create_exercises.sql`: `exercises` and
      `exercise_attempts` tables (FKs to `lessons`/`users`, indexes on
      `lesson_id` and `exercise_id` matching the `lessons_course_id_idx` /
      `ulp_user_id_idx` pattern)
- [x] 2.2 Seed 2-3 exercises (mix of MCQ and fill-in-blank) for 1-2 of the
      existing N5 lessons in the same migration or a `V9` seed, matching the
      V7 seed's fixed-UUID style

## 3. Grading + service layer

- [x] 3.1 Create `ExerciseService`: `listByLesson(lessonId)`,
      `submitAttempt(userId, exerciseId, answer)` — normalize (trim +
      lowercase) for fill-in-blank, verbatim compare for MCQ
- [x] 3.2 On a correct attempt, call
      `ProgressService.markComplete(userId, lesson.courseId, lessonId, CompletionSource.EXERCISE)`
      (resolve courseId via the exercise's lesson, not a client-supplied value)
- [x] 3.3 Always persist the `ExerciseAttempt` (correct or not) before
      returning

## 4. API surface

- [x] 4.1 Create `dto/ExerciseDtos.kt`: `ExerciseResponse` (no answer field),
      `AttemptRequest` (`answer: String`), `AttemptResponse` (`correct: Boolean`)
- [x] 4.2 Add `GET /api/lessons/{lessonId}/exercises` (new controller or
      extend an existing one — match `ProgressController`'s auth pattern:
      `@AuthenticationPrincipal` + `UserRepository` lookup)
- [x] 4.3 Add `POST /api/exercises/{exerciseId}/attempts`, 404 if the
      exercise id doesn't exist

## 5. Tests

- [x] 5.1 `@WebMvcTest` controller test (follow `ProgressControllerTest`
      pattern): list exercises omits `correctAnswer`; correct MCQ attempt →
      `correct: true`; correct fill-in-blank with different case/whitespace →
      `correct: true`; incorrect attempt → `correct: false`; unknown
      exercise id → 404
- [x] 5.2 Verify a correct attempt triggers exactly one
      `user_lesson_progress` row and does not duplicate on a second correct
      attempt after completion (service-level or slice test)
- [x] 5.3 `sh gradlew ktlintCheck test bootJar --no-daemon` passes

## 6. Verification

- [x] 6.1 Local end-to-end via `docker compose up -d --build`: log in, list a
      seeded lesson's exercises, submit a correct answer, confirm the lesson
      shows complete on `GET /api/courses/{id}/progress` and an
      `exercise.completed` event with `source=EXERCISE` appears in the Kafka
      console consumer
- [x] 6.2 Update `docs/ROADMAP.md` M3 section (mark this slice done, note
      what remains) and append a `docs/DEVLOG.md` entry
