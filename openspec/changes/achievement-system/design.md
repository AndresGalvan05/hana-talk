## Context

`event-worker/internal/store/store.go`'s `RecordActivityAndUpdateStreak`
is the single place that reacts to `exercise.completed`. It has two
existing idempotency guards worth understanding precisely before adding a
third:

- `event_worker.daily_activity (user_id, activity_date)` — **day-
  granularity** dedup. `INSERT ... ON CONFLICT DO NOTHING`; if
  `RowsAffected() == 0` the function returns early and skips the streak
  update entirely, because the streak only cares about *distinct active
  days*, not how many lessons were completed on one of them.
- The streak upsert itself, gated behind that same day-guard.

Critically, **that day-guard cannot also gate achievement/completion-count
logic**: if a user completes two different lessons on the same calendar
day, the second event hits the day-guard's `RowsAffected() == 0` branch
and returns immediately — so any completion-counting logic placed after
it would silently miss the second lesson. Completion-count achievements
need their own idempotency dimension, keyed on `(user_id, lesson_id)`,
independent of the day-based one. This was found by reading the actual
function, not assumed.

Confirmed from `core-api`: `ProgressService.markComplete()` already
guards on `progressRepository.existsById(progressId)` before publishing
`exercise.completed` — so core-api itself only ever publishes the event
once per real `(user, lesson)` completion, ever. The only duplicate
scenario event-worker still has to handle is Kafka redelivery of that
same message (e.g. a crash between processing and offset commit), not
core-api re-publishing. `ExerciseCompleted` (`events/events.go`) already
carries `LessonID` — currently unused by `handle()`, just parsed and
discarded.

Current content scope (from `core-api`'s seed migrations): one course
(N5), 5 lessons total. Completion-count thresholds are chosen against
that reality, not an assumed larger catalog.

## Goals / Non-Goals

**Goals:**
- Evaluate achievements as a natural extension of the existing
  `exercise.completed` handling — same transaction, same event, no new
  topic or consumer.
- Keep the catalog fixed and hardcoded (see proposal's cut line) but
  structured as data (a Go slice) so evaluation is one small generic loop,
  not one `if` per achievement.
- Make completion counting correct under same-day multi-lesson
  completions and Kafka redelivery, per the Context section above.

**Non-Goals:** see proposal.md — no new Kafka topic, no DB-configurable
catalog, no retroactive backfill job, no progress-bar UI, no points/tiers.

## Decisions

- **New table `event_worker.lesson_completions (user_id UUID, lesson_id
  UUID, completed_at TIMESTAMPTZ, PRIMARY KEY (user_id, lesson_id))`**,
  separate from `daily_activity`. `INSERT ... ON CONFLICT DO NOTHING`,
  checking `RowsAffected()`: only a genuinely new row counts as a new
  distinct-lesson completion. This is the idempotency boundary for
  completion-count achievements — decoupled from the day-guard for the
  reason in Context. Total completions for evaluation purposes is
  `SELECT COUNT(*) FROM event_worker.lesson_completions WHERE user_id =
  $1`, computed inline in the same transaction only when a new row was
  actually inserted (so a redelivered duplicate does zero extra queries
  beyond the no-op insert).
- **New table `event_worker.user_achievements (user_id UUID,
  achievement_code TEXT, unlocked_at TIMESTAMPTZ, PRIMARY KEY (user_id,
  achievement_code))`.** Unlock is `INSERT ... ON CONFLICT DO NOTHING`
  per newly-crossed code — naturally idempotent against redelivery and
  against re-evaluating a user whose state hasn't changed enough to cross
  anything new.
- **`store.RecordActivityAndUpdateStreak` is restructured into
  `store.RecordExerciseCompletion(ctx, userID, lessonID, date) ([]string,
  error)`** (returns newly-unlocked achievement codes, mainly for
  logging/observability — the frontend re-fetches the catalog rather than
  trusting a push). Inside one transaction:
  1. Day-guard insert into `daily_activity` + streak upsert, exactly as
     today, **unchanged**.
  2. Lesson-guard insert into `lesson_completions`; if it actually
     inserted, re-read `current_streak` and the new `COUNT(*)` and run
     achievement evaluation against both numbers.
  Steps 1 and 2 are independent — a same-day-but-different-lesson event
  skips step 1's streak recompute but still runs step 2.
- **Achievement catalog is a hardcoded Go slice** in a new
  `internal/achievements/catalog.go`:
  ```go
  type Definition struct {
      Code        string
      Kind        string // "streak" | "completions"
      Threshold   int
      Title       string
      Description string
  }
  var Catalog = []Definition{
      {Code: "STREAK_3", Kind: "streak", Threshold: 3, ...},
      {Code: "STREAK_7", Kind: "streak", Threshold: 7, ...},
      {Code: "STREAK_30", Kind: "streak", Threshold: 30, ...},
      {Code: "LESSONS_1", Kind: "completions", Threshold: 1, ...},
      {Code: "LESSONS_3", Kind: "completions", Threshold: 3, ...},
      {Code: "LESSONS_5", Kind: "completions", Threshold: 5, ...},
  }
  ```
  Evaluation itself is a pure function, `achievements.Evaluate(currentStreak,
  totalCompletions int, alreadyUnlocked map[string]bool) []string`,
  returning newly-crossed codes — mirroring the existing `streak.Next`
  package's pure/unit-testable shape rather than being inlined into
  `store.go` where it could only be exercised against a real database.
  `store.go` calls it, then `INSERT ... ON CONFLICT DO NOTHING`s each
  returned code — a code already unlocked never appears in `Evaluate`'s
  output (it's filtered by `alreadyUnlocked`), so the `ON CONFLICT` is a
  pure safety net against concurrent/redelivered evaluation, not the
  primary duplicate-prevention mechanism.
- **`GET /users/{userId}/achievements` (event-worker) returns the full
  catalog**, each entry annotated with `unlocked: bool` and `unlockedAt:
  *string`, computed by left-joining the catalog against
  `user_achievements` for that user — not just the unlocked subset, so
  the frontend can render locked badges without hardcoding the catalog
  client-side too.
- **core-api**: `EventWorkerClient.getAchievements(userId): List<AchievementDto>`,
  same injected-`RestClient.Builder` pattern as `getStreak`/`getLeaderboard`.
  New method on `UserProfileController` (`GET /api/users/me/achievements`)
  rather than a new controller — it's a third piece of "about me" state
  alongside profile and streak, same auth/resolution shape, no reason to
  split it into its own controller.
- **Frontend**: new `AchievementsPage.tsx`, badge grid using the existing
  `.card` class (matching `ProfilePage`/`LeaderboardPage`), unlocked
  badges rendered normally with their unlock date, locked badges
  `opacity`-dimmed with just title/description (no threshold numbers
  shown, to avoid a half-built progress-bar feel per the non-goals). New
  nav link next to Flashcards/Chat practice in the shared layout.

## Risks / Trade-offs

- **[Risk] No retroactive backfill** — see proposal's non-goals. A user
  already past a threshold at deploy time only unlocks it on their next
  `exercise.completed` event, since evaluation reads current cumulative
  state (not a delta) at that moment. In practice this self-heals on
  their very next activity, so no manual intervention is anticipated —
  but it is a real, accepted gap for the small number of already-active
  test/demo accounts.
- **[Risk] Achievement catalog changes require a deploy, not just data
  edits.** Accepted per non-goals — this app has no operational need to
  add achievements without a code change, and a hardcoded slice is far
  simpler to test and reason about than a rules table.
- **[Risk] `lesson_completions` duplicates information already
  derivable from core-api's own `user_lesson_progress` table** (which
  `event-worker` doesn't have direct DB access to, by the existing
  cross-service ownership-boundary decision from M4). Accepted — this is
  the same "own projection from the events you consume" pattern
  `event_worker.users` already uses for identity, not a new kind of
  duplication.

## Migration Plan

New Go-migrate pair `0002_achievements.up.sql` / `.down.sql` in
`event-worker/internal/migrations/`, creating `lesson_completions` and
`user_achievements` in the `event_worker` schema (same schema as
existing tables, no new schema). No core-api migration — this is
entirely event-worker + a new proxy endpoint. Deploys through the
existing event-worker and core-api CI → GHCR → `kubectl rollout restart`
paths; frontend ships in the same rollout since the achievements page has
nothing to show without the backend change.
