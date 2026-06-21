# event-worker

**Status:** Not started — Phase 2 (weeks 5–7)

**Stack:** Go, Apache Kafka (KRaft mode)

**Responsibility:** Consume Kafka events and handle async side effects.

- Consumer group subscribing to: `user.registered`, `exercise.completed`, `streak.updated`
- Welcome notification on registration
- Streak calculation on exercise completion
- Leaderboard updates on streak changes
- Small REST endpoint for the frontend to read leaderboard/streak state

Designed to demonstrate idiomatic Go: goroutines and channels used deliberately,
not a Java clone in Go syntax. Kafka runs in KRaft mode (no ZooKeeper).
