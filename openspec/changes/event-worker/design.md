## Context

`EventPublisher.kt` (core-api) publishes two topics, both keyed by
`userId`, best-effort (`max.block.ms=3000`, synchronous send failures
caught and logged — never fails the user-facing request):
- `user.registered`: `{userId, username, nativeLanguage, occurredAt}`.
- `exercise.completed`: `{userId, lessonId, courseId, jlptLevel, source,
  occurredAt}` — published from `ProgressService.markComplete` for *every*
  completion (`CompletionSource.MANUAL`, `AUTO`, or `EXERCISE`), not only
  exercise-graded ones, and only once ever per `(userId, lessonId)` since
  `markComplete` no-ops if a progress row already exists. Both topics have
  been running in-cluster since M2 with zero consumers.

`event-worker/README.md` already sketches the shape: Go, its own Postgres
schema, idempotent handlers, a small internal read API core-api proxies.
No `go` toolchain is installed on this dev machine yet (same situation
Node.js was in before M1 — installed when first needed).

## Goals / Non-Goals

**Goals:**
- Consume both topics into a dedicated `event_worker` Postgres schema,
  with no reads/writes into core-api's tables — a real ownership
  boundary, not just a naming convention.
- Day-granularity current streak per user, correct under Kafka's
  at-least-once redelivery, without a separate dedup ledger.
- A leaderboard and per-user streak, readable by core-api through a small
  internal HTTP API — never exposed to the frontend directly.
- Demonstrate idiomatic Go: goroutines/channels used with a clear purpose
  (fan-in from two topic consumers to one DB-writing goroutine), stdlib
  `net/http` (Go 1.22+ method+path routing) instead of a router
  dependency, `pgx`/plain SQL instead of an ORM.

**Non-Goals:**
- No notifications, no `streak.updated` topic, no Redis (see proposal's
  cut line).
- No cross-schema joins or foreign keys into core-api's `users`/`lessons`
  tables — `event-worker` maintains its own minimal `users` projection
  instead.
- No exactly-once processing — at-least-once + idempotent writes is the
  explicit, documented choice (this *is* the interview story, not a gap
  to close).
- No change to `EventPublisher`, topic names, or event payload shapes.

## Decisions

**Idempotency via a natural key, not a dedup ledger.** A
`daily_activity(user_id, activity_date)` table with a UNIQUE constraint on
both columns absorbs both same-day multiple completions and
Kafka-redelivered duplicates via `ON CONFLICT DO NOTHING` — no separate
"have I seen this Kafka offset before" table is needed. This is simpler
than a generic dedup ledger and happens to be exactly the right shape for
streak semantics anyway (a day either has activity or it doesn't).
Consumer offsets are committed only *after* a successful DB write, so a
crash mid-processing causes reprocessing (safe, idempotent) rather than
silent data loss.

**`user.registered` upserted, not append-only.** The local `users`
projection is `INSERT ... ON CONFLICT (user_id) DO UPDATE SET
username = EXCLUDED.username`, so redelivery and (hypothetical) username
changes are both handled by the same upsert — no special-casing.

**Streak recomputed from `daily_activity`, not stored as a running
counter that could drift.** On each new activity day, look at the most
recent prior activity date for that user: same day → no-op (already
counted); exactly one day earlier → increment stored `current_streak`;
any larger gap → reset to 1. A separate `user_streaks(user_id,
current_streak, last_active_date)` table caches this for cheap leaderboard
reads, but it's always derived from `daily_activity`, never treated as
the source of truth — recomputing from scratch is possible if the cache
ever needs rebuilding.

**No cross-service DB access — `event-worker` keeps its own `users`
projection.** Rejected alternative: give `event-worker` read access to
core-api's `users` table (same physical Postgres instance, so technically
possible). Rejected because it defeats the entire ownership-boundary point
of this milestone — the interview story is specifically about *not*
reaching across a service boundary through the database, even when the
database happens to be shared infrastructure. The trade-off is explicit
eventual consistency: a leaderboard entry can show without a username for
the (usually sub-second, since compose/cluster networking is fast) window
between an `exercise.completed` event and that user's `user.registered`
event being processed — extremely unlikely in practice since registration
always precedes any lesson completion, but architecturally possible and
worth stating rather than assuming away.

**Go dependencies kept minimal and idiomatic:** `github.com/segmentio/
kafka-go` (pure Go, no cgo/librdkafka, unlike `confluent-kafka-go` —
simpler cross-compilation) for the consumer; `github.com/jackc/pgx/v5`
(via `database/sql` or its native interface) for Postgres, no ORM;
stdlib `net/http`'s Go 1.22+ enhanced `ServeMux` (`"GET /leaderboard"`
pattern routing) instead of a third-party router — there's no routing
complexity here that justifies the dependency.

**Migrations owned entirely by `event-worker`.** A `migrations/` directory
(embedded via Go's `embed` package) applied via `golang-migrate` on
startup, targeting the `event_worker` schema specifically (not `public`).
Mirrors Flyway's role in core-api but is a fully separate migration
history — nothing about `event-worker`'s schema is coordinated with
core-api's Flyway migrations, reinforcing the ownership boundary.

**Dockerfile cross-compiles natively, no QEMU workaround needed.** Unlike
core-api's Gradle build (which needs the `$BUILDPLATFORM`/native-Gradle
trick to avoid running the JVM build under emulation), Go cross-compiles
directly via `GOOS`/`GOARCH` — the builder stage runs on `$BUILDPLATFORM`
and simply sets `GOARCH=$TARGETARCH` before `go build`, producing a
correct-arch static binary with zero emulation at any point. Worth calling
out explicitly in the Dockerfile comment as a contrast to core-api's.

## Risks / Trade-offs

- [Leaderboard shows a stale/missing username in the rare
  registration-event-not-yet-processed window] → accepted (Non-Goals);
  the streak count itself is always correct, only the display name can
  lag momentarily.
- [Two independent topic consumers feeding one DB-writing goroutine
  introduces a small amount of concurrency complexity for a
  portfolio-scale workload] → deliberate: this is the "idiomatic Go"
  interview story the service's README already commits to; a single
  sequential consumer loop would be simpler but wouldn't demonstrate
  anything Go-specific.
- [`event_worker` schema lives in the same Postgres instance as core-api's
  `public` schema — a single point of failure/resource contention] →
  matches the existing free-tier reality (one Postgres instance) already
  accepted for M1–M3; a separate database instance is out of scope.

## Migration Plan

New service, new schema — no existing data to migrate. Rollout: deploy
`event-worker` + apply its own migrations on startup (independent of
core-api's Flyway run), then core-api's `EventWorkerClient`
config/endpoints. If `event-worker` is unreachable, `/api/leaderboard` and
`/api/users/me/streak` return a 5xx (same synchronous-call philosophy as
`AiExerciseSvcClient` — there's no meaningful degraded response for a
read-only stats endpoint). Rollback: revert core-api's image tag; the two
new endpoints simply stop existing, nothing else is affected.

## Open Questions

- Exact leaderboard size (top 10? top 50?) and whether ties are broken by
  `last_active_date` — a product-scope detail, not architectural; left to
  implementation, not blocking this proposal.
