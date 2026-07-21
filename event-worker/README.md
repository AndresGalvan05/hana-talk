# event-worker

**Status:** Working — Milestone 4 done 2026-07-20

**Stack:** Go, Apache Kafka (KRaft mode)

**Responsibility:** Consume Kafka events and handle async side effects.

- Consumer group subscribing to `user.registered` and `exercise.completed`
  (the two topics core-api publishes — see `KafkaTopics.kt`).
- Streak calculation on exercise completion (day granularity).
- Leaderboard ranked by current streak.
- Small internal REST read API (leaderboard, per-user streak) that the Core
  API gateway proxies — the frontend never calls this service directly.
- Idempotent handlers: at-least-once delivery is assumed, processed events
  are tracked.

Owns its own storage (separate schema in the shared Postgres instance — one
DB server on free-tier hosting, but a real ownership boundary). Designed to
demonstrate idiomatic Go: goroutines and channels used deliberately, not a
Java clone in Go syntax.
