## Context

core-api already has a working completion mechanism:
`ProgressController` → `ProgressService.markComplete(userId, lessonId,
courseId, source)` → upserts `user_lesson_progress` (composite PK, idempotent
via `existsById`) → publishes `exercise.completed` on Kafka (best-effort,
`EventPublisher`). `CompletionSource` currently has `MANUAL` (the "Mark as
complete" button) and an unused `AUTO`. This change adds a third path —
graded exercise attempts — without inventing a second completion mechanism.

There is no `ai-exercise-svc` yet and no LLM key. Exercises for this change
are Flyway-seeded, the same way the N5 course/lessons were seeded in V7. The
domain model must not encode "how the exercise was authored" — a later
change adds a creation path (LLM-generated, written by `ai-exercise-svc`),
and the grading/attempt code shouldn't need to change when that lands.

## Goals / Non-Goals

**Goals:**
- Users can fetch the exercises attached to a lesson and submit an answer.
- Grading is synchronous and exact-match against a server-side-only answer.
- A correct attempt reuses `ProgressService.markComplete` — one completion
  mechanism, one Kafka event shape, no divergence.
- Multiple attempts are allowed; only the first correct one triggers
  completion (idempotent, matches existing `markComplete` behavior).

**Non-Goals:**
- No LLM calls, no `ai-exercise-svc`, no MongoDB — that's a separate change.
- No exercise authoring/admin API — exercises arrive via Flyway seed only,
  same as courses/lessons pre-M5 (admin role is M5).
- No frontend exercise UI — nothing to demo yet without generated content;
  building it now would mean throwing it away or reworking it once
  `ai-exercise-svc` exists.
- No partial-credit or free-text/LLM-graded scoring (roadmap cut for M3).

## Decisions

**Two exercise types, one table.** `Exercise` has `type` (`MCQ` |
`FILL_IN_BLANK`), `prompt`, `options` (nullable, JSON array of strings — only
for MCQ), `correctAnswer` (string — for MCQ it's the option text; for
fill-in-blank it's the accepted answer). One table instead of two avoids a
premature type hierarchy for two variants that both reduce to
"string equality after normalization." Fill-in-blank grading trims and
lowercases both sides before comparing; MCQ compares the submitted answer to
`correctAnswer` verbatim (options are fixed strings, no locale ambiguity).

**`correctAnswer` never serializes to the client.** The DTO returned by the
list endpoint (`ExerciseResponse`) omits it entirely — not just via
`@JsonIgnore` on the entity, which is easy to accidentally lift with a
different mapper later. Enforced by keeping DTO and entity separate (existing
pattern: `CourseDtos.kt`, `LessonDtos.kt` already don't reuse entities as
response bodies).

**Reuse `markComplete`, add `CompletionSource.EXERCISE`.** Alternative
considered: a parallel `exercise_attempts`-only completion check in the
progress endpoint. Rejected — `user_lesson_progress` is already the single
source of truth for "is this lesson done," and duplicating that logic risks
the two paths disagreeing (e.g. progress bar counts a lesson the exercise
system doesn't know is done, or vice versa). `ExerciseService` calls
`ProgressService.markComplete(userId, lessonId, courseId, CompletionSource.EXERCISE)`
directly on a correct attempt; `markComplete` is already idempotent
(`existsById` short-circuit), so re-submitting a correct answer after the
lesson is already complete is a safe no-op.

**Endpoint shape:**
`GET /api/lessons/{lessonId}/exercises` (list, answers stripped) and
`POST /api/exercises/{exerciseId}/attempts` (submit, body `{ answer: string }`,
response `{ correct: boolean }`). Nested under `lessons` for the list (matches
`ProgressController`'s `/api/courses/{courseId}/lessons/{lessonId}/complete`
nesting style) but flat under `exercises` for attempts, since an attempt only
needs the exercise id — courseId/lessonId are looked up server-side via the
exercise → lesson relation, not trusted from the client.

**Attempts are recorded even when wrong.** `ExerciseAttempt` stores every
submission (userId, exerciseId, submittedAnswer, isCorrect, attemptedAt) —
cheap, and gives M4's event-worker or a future stats view real data instead
of only the success case. No new Kafka event for this — recording is a
direct-write concern, not a cross-service side effect (same reasoning as why
`user_lesson_progress` writes don't get their own topic beyond
`exercise.completed`).

## Risks / Trade-offs

[Seeded content is a placeholder, not real exercise coverage] → acceptable:
this change's job is the grading/progress plumbing, not exercise authoring;
`ai-exercise-svc` replaces the seed as the real content source in the next
change, and the domain model doesn't need to change when that happens.

[Two lesson-completion triggers now exist — a button and an exercise] →
intentional per ROADMAP (M1's manual button stays; M3 adds the graded path)
and already accounted for by `CompletionSource` existing as an enum before
this change.

[No admin/authoring API means fixing bad seed content requires a new Flyway
migration] → matches existing course/lesson content practice; a real
authoring path is explicitly M5 (admin role) per ROADMAP, not this change's
problem to solve early.
