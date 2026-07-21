## Why

core-api has published `user.registered` and `exercise.completed` to Kafka
since M1/M2, but nothing has ever consumed them — Kafka has been running
in-cluster purely as a publish-side demo. M4's interview story is the
consumer side: a Go service reading those topics to compute day-granularity
streaks and a leaderboard, with idempotent handling under Kafka's
at-least-once delivery. This also gives the portfolio its first real
polyglot *read* path in the other direction (core-api calling out to a
service it doesn't own, similar in shape to `ai-exercise-svc` but for a
Go consumer rather than a Python generator).

## What Changes

- New `event-worker` service (Go): a Kafka consumer group subscribed to
  `user.registered` and `exercise.completed`, maintaining its own storage
  in a dedicated `event_worker` Postgres schema (same shared instance,
  real ownership boundary — no cross-schema queries into core-api's
  tables).
- **Streak calculation, day granularity**: `exercise.completed` fires for
  *any* lesson completion (manual or exercise-graded — the event name is
  historical, `ProgressService.markComplete` publishes it regardless of
  `CompletionSource`). Each event's UTC date is recorded as one activity
  day per user; a UNIQUE `(user_id, activity_date)` constraint makes
  same-day and redelivered events naturally idempotent — multiple
  completions on the same day, or a Kafka-redelivered duplicate, both
  collapse to a single activity row with no separate dedup ledger needed.
  Current streak is derived from consecutive activity days ending today
  or yesterday.
- **Local `users` projection**: `event-worker` builds its own
  `(user_id, username)` table by consuming `user.registered` (upserted,
  naturally idempotent), so the leaderboard can show usernames without
  ever querying core-api's database directly — an explicit eventual-
  consistency boundary (a leaderboard entry may briefly lack a username if
  its `user.registered` event hasn't been processed yet; the streak itself
  is unaffected).
- **Internal read API** (Go, plain `net/http`): `GET /leaderboard` (top N
  by current streak) and `GET /users/{userId}/streak`. Not exposed
  publicly — only core-api calls it, over the compose/cluster-internal
  network.
- **core-api proxy endpoints**: `GET /api/leaderboard` and
  `GET /api/users/me/streak` (added to the existing `UserProfileController`
  alongside `/me` and `/me/level`), each calling `event-worker`'s internal
  API via a new `EventWorkerClient` (same `RestClient`-with-timeout shape
  as `AiExerciseSvcClient` from M3). Both endpoints are authenticated by
  default via `SecurityConfig`'s existing `anyRequest().authenticated()`
  catch-all — no `SecurityConfig` changes needed.
- Infra: new `event-worker` compose service (depends on postgres + kafka
  healthy), a Go multi-stage Dockerfile (true cross-compile via
  `GOARCH`/`GOOS` — no QEMU-under-Gradle-style workaround needed, unlike
  core-api's JVM build), and `.github/workflows/event-worker.yml`
  (`go vet`, `go test`, multi-arch build + push to GHCR).

## Capabilities

### New Capabilities
- `streaks-leaderboard`: event ingestion idempotency, day-granularity
  streak calculation, and the leaderboard/per-user-streak read API
  (internal in `event-worker`, proxied through core-api).

### Modified Capabilities
(none — `exercise-grading`/`exercise-generation` event publishing is
consumed as-is; core-api's two new endpoints are purely additive and rely
on `SecurityConfig`'s existing default-authenticated catch-all)

## Impact

- **New service**: `event-worker/` (Go, `kafka-go` for the consumer,
  `pgx` for Postgres — no ORM, plain SQL, consistent with the "idiomatic
  Go, not a Java clone" goal from the service's own README).
- **core-api**: `UserProfileController` gains `GET /me/streak`; new
  `LeaderboardController` for `GET /api/leaderboard`; new
  `EventWorkerClient` component (mirrors `AiExerciseSvcClient`).
- **infra**: `docker-compose.yml` gains an `event-worker` service; new
  Dockerfile + CI workflow.
- **Non-goals / cut line** (per `docs/ROADMAP.md`): no notifications or
  emails on streak milestones; no `streak.updated` Kafka topic (the
  leaderboard is read on-demand from `event-worker`'s own storage, not
  pushed); no Redis (Postgres is the only store, matching the free-tier
  single-Postgres-instance reality); no longest-streak tracking or
  streak-freeze/grace-day mechanics — current streak only.
- **Milestone**: M4, per `docs/ROADMAP.md`.
