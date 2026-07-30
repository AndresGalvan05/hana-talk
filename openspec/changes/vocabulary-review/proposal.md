## Why

Every lesson's vocabulary list is currently write-once, read-many: shown
on the lesson page and never revisited. There's no mechanism to actually
retain it once you've moved past a lesson. This slice adds spaced
repetition — a daily review queue that resurfaces vocabulary you've
learned on a schedule that adapts to whether you're getting it right,
which was deliberately deferred when `vocabulary_items` became a real
table (not JSON) specifically so this slice could be added without a
breaking migration.

## What Changes

- New `user_vocabulary_progress` table (composite key `user_id` +
  `vocabulary_item_id`, mirroring `user_lesson_progress`'s
  `@EmbeddedId` pattern): `next_review_at`, `interval_days`,
  `correct_streak`. Unlike every other per-user table in this codebase
  (`user_lesson_progress`, `exercise_attempts` — both append-only logs),
  this one is genuinely mutated in place, since it tracks current
  scheduling state, not a history of events.
- Simple Leitner-style scheduling, not full SM-2: a correct review
  doubles the interval (capped at 90 days) and increments the streak; an
  incorrect review resets the interval to 1 day and the streak to 0.
  Easier to reason about and verify than SM-2's ease-factor math, while
  still a real spaced-repetition story.
- New `GET /api/vocabulary/review`: today's due queue, scoped to
  vocabulary from lessons the user has actually completed (via existing
  `user_lesson_progress`) — not the entire vocabulary catalog regardless
  of whether they've seen it. An item with no progress row yet (never
  reviewed) counts as due.
- New `POST /api/vocabulary-items/{id}/review`: records a correct/
  incorrect result, applies the scheduling rule, upserts the progress
  row, and returns the new schedule.
- New frontend `/flashcards` page: one card at a time, click to reveal
  the reading and meaning, then mark it correct or incorrect to advance
  to the next card. An empty queue shows a clear "nothing due" state
  rather than looking broken.

## Capabilities

### New Capabilities
- `vocabulary-review`: the scheduling model and the two core-api
  endpoints (due-queue query, review submission). No LLM/`ai-exercise-svc`
  involvement — this is pure CRUD/scheduling logic, unlike every prior
  slice.
- `vocabulary-review-ui`: the frontend flashcard page.

### Modified Capabilities
(none)

## Impact

- `core-api/`: new Flyway migration `V13__create_vocabulary_progress.sql`;
  new `UserVocabularyProgress` entity + repository (mirroring
  `UserLessonProgress`'s composite-key pattern); new
  `VocabularyReviewController` (or added to the existing
  `VocabularyController` — see design.md); scheduling logic in a new
  `VocabularyReviewService`.
- `frontend/`: new `FlashcardsPage.tsx`, a new `/flashcards` route, a nav
  link.
- No changes to `ai-exercise-svc`, `event-worker`, or Kafka — this slice
  is entirely within core-api and the frontend.

## Non-goals / cut line

- No full SM-2 (ease factors, per-card difficulty tuning) — Leitner-style
  doubling/reset is simpler to implement and verify, and still
  demonstrates real spaced-repetition scheduling for the interview story.
- No daily new-card cap (a common Anki-style "don't show more than N new
  cards per day" limit). At this app's actual scale (~70-75 vocabulary
  items across the current 5-lesson course), a first-time review session
  showing everything due isn't overwhelming the way it would be in a
  large-catalog app — a real cut, not an oversight, revisit if the
  catalog grows substantially.
- No review streak/Kafka/leaderboard tie-in — a flashcard review does not
  publish `exercise.completed` or otherwise count toward the existing
  streak system. Keeps this slice's scope to the review mechanic itself.
- No cross-device sync beyond what the shared database already gives for
  free — no offline mode, no local caching of due items.

## Milestone

Post-roadmap slice (M1–M5 complete). Slice 4 of the deepening-
interactivity plan from `structured-lesson-content`'s proposal — slices 1
through 3 (`structured-lesson-content`, `new-exercise-types`,
`ai-conversation-practice`) and the interleaved `profile-and-progress`
slice are already shipped and archived.
