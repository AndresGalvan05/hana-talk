## 1. event-worker: schema

- [x] 1.1 New `event-worker/internal/migrations/0002_achievements.up.sql`
      (+ matching `.down.sql`): `event_worker.lesson_completions(user_id
      UUID NOT NULL, lesson_id UUID NOT NULL, completed_at TIMESTAMPTZ
      NOT NULL, PRIMARY KEY (user_id, lesson_id))` and
      `event_worker.user_achievements(user_id UUID NOT NULL,
      achievement_code TEXT NOT NULL, unlocked_at TIMESTAMPTZ NOT NULL,
      PRIMARY KEY (user_id, achievement_code))`

## 2. event-worker: catalog + pure evaluation

- [x] 2.1 New `internal/achievements/catalog.go`: `Definition{Code, Kind,
      Threshold, Title, Description}` and the fixed `Catalog` slice —
      `STREAK_3`/`STREAK_7`/`STREAK_30` (kind `"streak"`),
      `LESSONS_1`/`LESSONS_3`/`LESSONS_5` (kind `"completions"`)
- [x] 2.2 New `internal/achievements/evaluate.go`: pure
      `Evaluate(currentStreak, totalCompletions int, alreadyUnlocked
      map[string]bool) []string`, returning codes whose kind/threshold is
      newly met and that aren't already in `alreadyUnlocked`
- [x] 2.3 `internal/achievements/evaluate_test.go`: no achievements when
      under every threshold; exactly the right codes returned when a
      streak threshold is newly crossed; exactly the right codes when a
      completions threshold is newly crossed; an already-unlocked code is
      never returned even if its threshold is still met; multiple
      thresholds crossed at once (e.g. streak jumps past both 3 and 7 in
      one evaluation) all return together

## 3. event-worker: store changes

- [x] 3.1 New `store.RecordExerciseCompletion(ctx, userID, lessonID
      string, date time.Time) ([]string, error)` replacing
      `RecordActivityAndUpdateStreak` as the entry point called from
      `consumer.go`. One transaction: (a) existing day-guard +
      streak-upsert logic, unchanged; (b) separately, `INSERT INTO
      lesson_completions ... ON CONFLICT (user_id, lesson_id) DO NOTHING`
      — only if that insert's `RowsAffected() == 1`, read the current
      streak, `SELECT COUNT(*) FROM lesson_completions WHERE user_id =
      $1`, and the user's already-unlocked codes from
      `user_achievements`, call `achievements.Evaluate`, and `INSERT ...
      ON CONFLICT DO NOTHING` each returned code into `user_achievements`
      with `unlocked_at = date`; return the newly-unlocked codes
- [x] 3.2 `consumer.go`'s `handle()` for `TopicExerciseCompleted`: pass
      `e.LessonID` through and call `RecordExerciseCompletion` instead of
      `RecordActivityAndUpdateStreak`
- [x] 3.3 New `store.GetAchievements(ctx, userID string)
      ([]AchievementStatus, error)`: left-join `achievements.Catalog`
      (in Go, not SQL — the catalog isn't a table) against a query of
      that user's `user_achievements` rows, returning one entry per
      catalog definition with `Unlocked bool` and `UnlockedAt
      *time.Time`

## 4. event-worker: HTTP + wiring

- [x] 4.1 New `GET /users/{userId}/achievements` handler in
      `internal/api/server.go`, added to the existing `Store` interface
      there (`GetAchievements(...)`) and the `fakeStore` test double in
      `server_test.go`
- [x] 4.2 `server_test.go`: new tests for the achievements handler — a
      user with a mix of locked/unlocked entries returns the full
      catalog with correct flags; a user with none unlocked returns the
      full catalog all locked

## 5. core-api: proxy

- [x] 5.1 `EventWorkerClient.getAchievements(userId: UUID):
      List<AchievementDto>` (new DTO: `code`, `title`, `description`,
      `unlocked: Boolean`, `unlockedAt: Instant?`), same injected
      `RestClient.Builder` pattern as `getStreak`/`getLeaderboard`
- [x] 5.2 `UserProfileController`: new `GET /api/users/me/achievements`
      method, resolving the user the same way as its existing methods
- [x] 5.3 `UserProfileControllerTest`: 401 without auth, 200 with a
      mocked `EventWorkerClient` response asserting the JSON shape

## 6. Frontend

- [x] 6.1 New `frontend/src/pages/AchievementsPage.tsx`: fetches
      `/api/users/me/achievements`, renders a grid of `.card` badges —
      unlocked ones show title/description/unlock date normally, locked
      ones show title/description only, visually dimmed (`opacity`/muted
      styling), no threshold numbers shown
- [x] 6.2 New route + nav link (`App.tsx`, shared layout) alongside
      Flashcards/Chat practice
- [x] 6.3 `oxlint` and `tsc -b && vite build` both green

## 7. Local verification

- [x] 7.1 `docker compose up -d --build`; register a fresh user, complete
      lessons and/or wait across simulated days (or seed
      `daily_activity`/`lesson_completions` rows directly, matching this
      session's established DB-verification discipline) until at least
      one streak achievement and one completion achievement unlock;
      confirm via `GET /api/users/me/achievements` and the
      `/achievements` page that both show unlocked with a timestamp and
      the rest show locked. Verified: completed 3 lessons for a fresh
      user, unlocking `LESSONS_1` and `LESSONS_3` with real timestamps,
      confirmed both via curl and the actual `/achievements` page (badge
      grid, unlocked cards showing an unlock date, locked cards dimmed
      with none). Hit the same local-only Kafka consumer-group
      rebalancing flakiness noted in `profile-and-progress`'s DEVLOG
      entry (a fresh user's `exercise.completed` event sat unconsumed) —
      restarting the `event-worker` container made it rejoin cleanly and
      process the backlog; not a code defect in this change.
- [x] 7.2 Confirm a same-day, second-different-lesson completion still
      counts as a new distinct completion (the specific bug this design
      guards against) — complete two different lessons within the same
      UTC day and confirm the completions count increased by 2, not 1.
      Verified: completed lessons 2 and 3 (same UTC day as lesson 1);
      `lesson_completions` shows all 3 distinct rows and `LESSONS_3`
      unlocked, confirming the day-guard did not suppress the 2nd/3rd
      completion's counting.
- [x] 7.3 Confirm Kafka redelivery doesn't double-count: manually
      re-publish (or re-consume) the same `exercise.completed` message
      and confirm no duplicate `lesson_completions` row and no
      unlock-timestamp change. Verified: manually re-published lesson 1's
      exact `exercise.completed` payload via `kafka-console-producer`;
      `lesson_completions` count stayed at 3 and `user_achievements`
      unlock timestamps were unchanged.
- [x] 7.4 `go test ./...` (event-worker) and
      `sh gradlew ktlintCheck test --no-daemon` (core-api) both green

## 8. Production rollout

- [ ] 8.1 Deploy — merge to `main`, CI builds event-worker, core-api, and
      frontend images; `kubectl rollout restart` all three (event-worker
      migration runs on startup, same as `0001_init` did)
- [ ] 8.2 Spot-check the live site: complete a lesson (or use an existing
      test account with real history) and confirm at least one
      achievement appears unlocked

## 9. Docs

- [ ] 9.1 Update `docs/DEVLOG.md` (session entry) and `docs/ROADMAP.md`
      decision log
- [ ] 9.2 Update root `README.md`'s Core API surface table with the new
      `GET /api/users/me/achievements` row
