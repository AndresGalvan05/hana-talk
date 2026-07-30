## Context

`vocabulary_items(id, lesson_id, japanese, reading, meaning, position)`
already exists (`structured-lesson-content`), deliberately kept as a real
table rather than JSON specifically so this slice could add per-user
review state without a breaking migration. `user_lesson_progress` already
tracks lesson completion as a composite-key
(`UserLessonProgressId(userId, lessonId)`) row with no mutable fields
beyond its creation — `ProgressService.markComplete` is a clean
find-or-create pattern (`existsById` guard, then `save`, no updates). This
slice's `user_vocabulary_progress` needs the opposite: a row that gets
read back and *mutated* on every review, which no other table in this
codebase currently does — every other per-user table (`user_lesson_progress`,
`exercise_attempts`) is an append-only log.

## Goals / Non-Goals

**Goals:**
- Real spaced-repetition scheduling (Leitner-style), not a fake progress
  bar — the interval and streak visibly change based on review results.
- Scope the review queue to vocabulary from lessons the user has actually
  completed, using the existing `user_lesson_progress` data rather than
  introducing a new "vocabulary exposure" concept.

**Non-Goals:**
- See proposal.md's cut line — no SM-2, no new-card daily cap, no Kafka/
  streak tie-in.

## Decisions

- **`UserVocabularyProgress` uses a composite `@EmbeddedId`
  (`userId`, `vocabularyItemId`)**, exactly mirroring
  `UserLessonProgressId`'s existing pattern, rather than a surrogate UUID
  primary key. Consistent with the one other per-user-per-item table in
  the codebase, and a composite key is the natural fit for "at most one
  progress row per user per vocabulary item."
- **The due-queue query is computed in Kotlin, not a single complex JPQL
  join.** Three simple, already-precedented repository calls — completed
  lesson IDs from `UserLessonProgressRepository.findByIdUserId` (same
  method `ProgressService.getCourseProgress` already uses), vocabulary
  items for those lessons via a new
  `VocabularyItemRepository.findByLessonIdIn`, and this user's existing
  progress rows via a new
  `UserVocabularyProgressRepository.findByIdUserId` — then filtered/
  joined in the service layer. Matches this codebase's consistent
  preference for simple Spring Data derived queries plus in-memory
  composition over hand-written JPQL, and keeps the "is this item due"
  logic as plain, testable Kotlin rather than buried in a query string.
- **Review submission does a read-then-write, not an atomic upsert.**
  `VocabularyReviewService.submitReview` loads the existing progress row
  (if any) via `findByIdOrNull`, computes the new interval/streak in
  Kotlin from the current values (or the "first review" defaults if no
  row exists), and saves. This is a single-user-driven write on the
  request path (no concurrent-writer race to worry about, unlike Kafka
  consumption), so the simplicity of read-then-write outweighs the
  marginal safety of a database-level upsert here.
- **`VocabularyReviewController` is a new controller, not added to the
  existing `VocabularyController`.** The existing one is scoped to
  `GET /api/lessons/{lessonId}/vocabulary` (lesson-scoped, no auth
  needed, no review concept) — genuinely different resource shape and
  auth requirements from `/api/vocabulary/review` and
  `/api/vocabulary-items/{id}/review` (both user-scoped, both
  authenticated). Splitting them keeps each controller's dependencies
  and auth story uncomplicated rather than mixing a public lesson-content
  read with authenticated per-user state in one class.
- **Interval cap of 90 days** is a fixed constant (mirroring
  `MIN_EXERCISES` in `ai-exercise-svc/app/schemas.py` as a named-constant
  precedent, not a magic number inline), not configurable — no product
  need for it to be tunable yet.

## Risks / Trade-offs

- **[Risk] A user with a huge completed-lesson history could have an
  expensive due-queue computation once the catalog grows well past
  today's ~70-75 items** → Mitigation: not a concern at current scale;
  revisit with a database-level query (or pagination) if the catalog
  grows by an order of magnitude, same reasoning already applied to the
  leaderboard's un-paginated top-20.
- **[Risk] Leitner-style doubling is cruder than SM-2 and can over- or
  under-schedule compared to a "real" spaced-repetition algorithm** →
  Accepted deliberately (see proposal's non-goals) — the interview story
  is "I understand spaced repetition and picked the simpler, verifiable
  algorithm deliberately," not "I implemented SM-2 perfectly."

## Migration Plan

New Flyway migration `V13__create_vocabulary_progress.sql`: creates
`user_vocabulary_progress(user_id, vocabulary_item_id, next_review_at,
interval_days, correct_streak, PRIMARY KEY (user_id, vocabulary_item_id))`
with `REFERENCES users(id)` / `REFERENCES vocabulary_items(id)` foreign
keys, `ON DELETE CASCADE` on both (matching `exercise_attempts`'
precedent for per-user rows tied to content that could theoretically be
removed). No data migration needed — the table starts empty, every
vocabulary item is implicitly "due" for every user until first reviewed.
Deploy order: core-api only (this slice has no `ai-exercise-svc` or
frontend-only pieces to sequence around); frontend ships in the same
rollout since the new page has nothing to display without the backend.
