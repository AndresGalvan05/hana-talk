## Why

M3's goal is graded exercises with AI-generated content, but the grading and
progress-integration plumbing doesn't need an LLM to exist — it needs stored
questions and answers. Building that slice first (seeded exercises, not
generated ones) unblocks real progress now, while LLM key procurement
(Groq/Gemini/OpenRouter) happens in parallel, and gives `ai-exercise-svc`
(a later change) a stable domain to write into instead of co-designing it
under LLM-integration pressure.

**Milestone:** M3 — AI exercises (first slice; see `docs/ROADMAP.md`).
`ai-exercise-svc` + provider failover + frontend exercise UI are explicitly
out of scope here and follow in a separate change once LLM keys exist.

## What Changes

- New `exercise` domain in core-api: `Exercise` (belongs to a lesson; MCQ or
  fill-in-blank; stores the correct answer server-side only) and
  `ExerciseAttempt` (a user's submitted answer + correctness + timestamp).
- New endpoints: list a lesson's exercises (answers never serialized to the
  client) and submit an attempt (returns correctness, never the answer key).
- Grading is synchronous, in-process, exact-match against the stored answer —
  no LLM call anywhere in this change.
- A correct attempt marks the lesson complete through the **existing**
  `ProgressService.markComplete` path, reusing `user_lesson_progress` and the
  `exercise.completed` Kafka event — not a parallel completion mechanism.
- `CompletionSource` gains an `EXERCISE` value (currently `MANUAL`, `AUTO`).
- Flyway migration adds `exercises` and `exercise_attempts` tables, plus a
  seed of 2-3 exercises for the existing N5 lessons (placeholder content,
  same pattern as the V7 course/lesson seed) so the flow is demoable without
  `ai-exercise-svc`.

## Capabilities

### New Capabilities
- `exercise-grading`: users can fetch a lesson's exercises and submit answers
  for synchronous, stored-answer grading; a correct answer completes the
  lesson via the existing progress mechanism.

### Modified Capabilities
(none — no existing `openspec/specs/` capability covers course/lesson/progress
yet, so there is no delta spec to write; `exercise-grading` is additive.)

## Impact

- **core-api**: new `domain/exercise/` package (entities + repositories), new
  `ExerciseService`, new `ExerciseController` (or extend an existing
  controller — decided in design), `CompletionSource.EXERCISE`, Flyway `V8`.
- **Database**: two new tables (`exercises`, `exercise_attempts`), FK to
  `lessons` and `users`.
- **Kafka**: no new topic — reuses `exercise.completed` with
  `source=EXERCISE`, matching the payload shape `EventPublisher` already
  produces.
- **Frontend**: untouched by this change (exercise UI is a later change,
  once `ai-exercise-svc` exists and there's a generation flow to show).
- **Tests**: new `@WebMvcTest` controller test following the existing
  `ProgressControllerTest` / `LessonControllerTest` pattern.
