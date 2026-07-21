## 1. Go project scaffold

- [ ] 1.1 Install a Go toolchain on this machine if not already present
      (none installed as of this proposal)
- [ ] 1.2 `event-worker/go.mod`, `cmd/event-worker/main.go` skeleton
- [ ] 1.3 Add `github.com/segmentio/kafka-go` and `github.com/jackc/pgx/v5`
      dependencies
- [ ] 1.4 Config via env vars: `KAFKA_BOOTSTRAP_SERVERS`, `DB_URL`
      (or discrete host/user/password/dbname), `INTERNAL_API_PORT`

## 2. Schema and migrations

- [ ] 2.1 `migrations/` directory (embedded via `embed`), applied via
      `golang-migrate` on startup, targeting a dedicated `event_worker`
      Postgres schema (not `public`)
- [ ] 2.2 Migration: `users(user_id UUID PRIMARY KEY, username TEXT)`
- [ ] 2.3 Migration: `daily_activity(user_id UUID, activity_date DATE,
      PRIMARY KEY (user_id, activity_date))`
- [ ] 2.4 Migration: `user_streaks(user_id UUID PRIMARY KEY,
      current_streak INT NOT NULL DEFAULT 0, last_active_date DATE)`

## 3. Kafka consumer

- [ ] 3.1 Consumer group subscribed to `user.registered` and
      `exercise.completed`; one goroutine per topic reader, fanning
      decoded events into a shared channel consumed by a single
      DB-writing goroutine
- [ ] 3.2 `user.registered` handler: upsert into `users` (`ON CONFLICT
      (user_id) DO UPDATE SET username = EXCLUDED.username`)
- [ ] 3.3 `exercise.completed` handler: derive UTC date from
      `occurredAt`, insert into `daily_activity` with `ON CONFLICT DO
      NOTHING`; only if a new row was actually inserted, recompute
      `user_streaks` (compare the new date to `last_active_date`:
      same day → no-op, +1 day → increment `current_streak`, larger gap
      → reset to 1)
- [ ] 3.4 Commit consumer offsets only after the DB write succeeds

## 4. Internal read API

- [ ] 4.1 `GET /leaderboard` (top N by `current_streak` desc, joined
      against the local `users` projection for display names) using Go
      1.22+ `net/http` `ServeMux` method+path routing, no router
      dependency
- [ ] 4.2 `GET /users/{userId}/streak` — returns `{current_streak: 0,
      last_active_date: null}`-shaped response for a user with no
      activity yet, not an error
- [ ] 4.3 Tests: idempotent upsert (`user.registered` consumed twice
      leaves one row); `daily_activity` unique constraint absorbs a
      same-day duplicate and a redelivered event; streak increments on a
      consecutive day and resets after a gap; leaderboard/streak handlers
      return correct shapes (can run against a real local Postgres via
      `docker compose`, or a lightweight test double — pick whichever
      keeps the tests fast and is idiomatic Go, decide at implementation
      time)

## 5. core-api integration

- [ ] 5.1 New `EventWorkerClient` (mirrors `AiExerciseSvcClient`'s
      `RestClient`-with-timeout shape) with a base URL from
      `EVENT_WORKER_URL`
- [ ] 5.2 `UserProfileController` gains `GET /me/streak`, delegating to
      `EventWorkerClient`
- [ ] 5.3 New `LeaderboardController` for `GET /api/leaderboard`,
      delegating to `EventWorkerClient`
- [ ] 5.4 Tests (`@WebMvcTest`, matching existing controller test
      pattern): both endpoints require auth; both proxy through to a
      mocked `EventWorkerClient` and return its shape; a downstream
      failure surfaces as a 5xx

## 6. Infra wiring (local + CI)

- [ ] 6.1 Add `event-worker` service to `infra/docker-compose.yml`
      (depends on postgres + kafka healthy); add `EVENT_WORKER_URL` to
      `core-api`'s environment
- [ ] 6.2 `event-worker/Dockerfile`: Go multi-stage build, native
      `GOOS`/`GOARCH` cross-compile (no QEMU-under-build workaround
      needed, unlike core-api's Gradle build)
- [ ] 6.3 `.github/workflows/event-worker.yml`: `go vet`, `go test`,
      multi-arch build + push to GHCR (mirrors
      `core-api.yml`/`ai-exercise-svc.yml` structure)

## 7. Verification

- [ ] 7.1 `docker compose up -d --build`: register a fresh user, complete
      a lesson (manual or exercise), confirm `GET /api/users/me/streak`
      shows a streak of 1
- [ ] 7.2 Confirm `GET /api/leaderboard` includes that user with the
      correct username (sourced from `event-worker`'s own `users`
      projection, not core-api's database)
- [ ] 7.3 Restart the `event-worker` container mid-flow (simulating a
      consumer crash) and confirm no duplicate/incorrect streak state
      after it rejoins the consumer group and reprocesses
- [ ] 7.4 Confirm a user with no completions yet gets a 0 streak, not an
      error, from `GET /api/users/me/streak`
- [ ] 7.5 Update `docs/ROADMAP.md` (M4 done), `docs/DEVLOG.md` (session
      entry), `README.md` service table and status
