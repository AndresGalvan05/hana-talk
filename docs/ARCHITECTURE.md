# Architecture & Trade-offs

This is a synthesis, not new design work — every decision below was made
and dated somewhere in `docs/ROADMAP.md`'s decision log or `docs/DEVLOG.md`'s
session history across M1–M5. This document exists to make that legible
in one sitting, organized by the questions someone would actually ask in
a technical interview, rather than chronologically.

## 1. System overview

```
                    +-------------+
                    |   React     |
                    |  Frontend   |
                    +------+------+
                           | HTTPS (Cloudflare, Full-strict TLS)
                           v
              +------------------------+
              |   Core API (Gateway)   |
              |  Kotlin + Spring Boot  |
              |  - JWT auth + roles    |
              |  - Courses/lessons     |
              |  - Exercise grading    |
              |  - PostgreSQL          |
              +--+-------------+------+
    sync HTTP    |             |
  (exercise      |             | Kafka publish (user.registered,
   generation)   |             | exercise.completed) -- AND separately,
                 v             | sync HTTP for leaderboard/streak reads
   +--------------------+      v
   |  AI Exercise Svc   |   +------------------+
   |  Python + FastAPI  |   |  Event Worker    |
   |  - Gemini -> Groq   |   |  Go              |
   |    -> OpenRouter    |   |  - Kafka consumer|
   |  - MongoDB cache    |   |  - Streaks       |
   +--------------------+    |  - Leaderboard   |
                             |  - own Postgres  |
                             |    schema        |
                             +------------------+
```

core-api, `ai-exercise-svc`, and `event-worker` all export OTel traces
(core-api also exports metrics) via OTLP directly to Grafana Cloud — no
in-cluster collector/agent.

Two independent relationships cross the core-api ↔ event-worker boundary,
worth naming explicitly since they're easy to conflate: **Kafka**
(core-api publishes, event-worker consumes — async, one-directional) and
**HTTP** (core-api calls event-worker's small internal read API for
leaderboard/streak data — sync, request/response, the opposite direction
of "who owns the data"). `ai-exercise-svc` only ever appears on the HTTP
side; it publishes nothing to Kafka.

## 2. Why polyglot

The stack was fixed by design from the roadmap's approval (2026-07-13) —
not because any one piece needed that specific language, but because the
interview story is explicitly about defending three different technology
choices on their own terms rather than using one language everywhere:

- **Kotlin + Spring Boot** for the gateway: the one service everything
  else depends on benefits most from a mature ecosystem — Spring Security,
  Spring Data JPA, Flyway, and a JWT library that's a two-line dependency.
  This is also the only service with real domain complexity (auth,
  courses/lessons/exercises, progress, grading), where a batteries-included
  framework earns its weight.
- **Python + FastAPI** for `ai-exercise-svc`: LLM provider SDKs
  (`google-genai`, `openai`) are Python-first, and the ecosystem's
  structured-output tooling (Pydantic validation, `response_schema`) maps
  directly onto the "validate strictly, never persist garbage" requirement
  this service has. No other language would have made the actual LLM
  integration simpler.
- **Go** for `event-worker`: a Kafka consumer is I/O-bound and
  concurrency-shaped — exactly Go's strength, and a chance to demonstrate
  goroutines/channels used for something they're actually good at (a
  fan-in from two independent topic readers to one serialized DB-writing
  goroutine — see §5), not "a Java service written in Go syntax."

## 3. Sync vs. async: what waits, what doesn't

Two paths exist, and the split isn't accidental:

- **Exercise generation is synchronous.** When a user requests exercises
  for a lesson with none yet, core-api calls `ai-exercise-svc` on the
  request path and waits — because the user is, literally, waiting for
  exercises to appear. This path gets a real timeout (`90s`, tuned up from
  an initial `45s` after a forced-failure test measured `~62s` for
  Gemini's failure path alone before falling through to Groq — see
  `docs/DEVLOG.md` 2026-07-20) and a real failover chain (Gemini → Groq →
  OpenRouter, falling through on any failure). There's no sensible
  "degraded" response for "list exercises" when none exist — a failure
  here is a clean `502`, not a silently empty list.
- **Streaks and leaderboard are asynchronous**, via Kafka. Nothing is
  waiting on a streak update the instant a lesson completes — `event-worker`
  consumes `exercise.completed` independently, and a user checking their
  streak a few seconds later never notices the lag. This is the
  "realistic split, not Kafka for everything" the README has said since
  M3: Kafka is used where the async property is actually true, not because
  Kafka is already in the stack.

## 4. The outbox trade-off

`EventPublisher.kt` publishes `user.registered` and `exercise.completed`
as **fire-and-forget**: a synchronous `KafkaTemplate.send()` wrapped in a
try/catch, `max.block.ms=3000` capping how long a broker outage can stall
a user-facing request, and failures logged, not retried or persisted. This
was a real bug once (M1, 2026-07-13): `KafkaTemplate.send()` throws
**synchronously** on a metadata timeout, not just asynchronously via its
callback, so an unreachable broker originally broke registration outright
before the try/catch was added.

This is a deliberate, load-bearing scope cut, not an oversight — the
honest interview answer to "what happens if Kafka is down when a user
registers?" is: **registration still succeeds, the event is dropped, and
there is currently no replay mechanism.** A transactional outbox (write
the event to a Postgres table in the same transaction as the business
write, a separate relay process publishes it later with at-least-once
guarantees) is the standard fix, and was explicitly deferred — verified
working as designed by scaling Kafka to zero replicas in production
(2026-07-19) and confirming login/completion still returned `2xx` with
only the `max.block.ms` latency penalty.

## 5. Ownership boundaries under one shared Postgres

`event-worker` and core-api share one physical Postgres instance (free-tier
reality — one DB server, not a fleet), but `event-worker` **never queries
core-api's tables**. It owns a separate `event_worker` schema with its own
Flyway-equivalent migrations (`golang-migrate`, embedded via `//go:embed`,
a completely independent migration history from core-api's Flyway), and
maintains its own `(user_id, username)` projection built by consuming
`user.registered` — rather than joining against core-api's `users` table,
which it technically *could* reach given they're in the same instance.

The trade-off is explicit eventual consistency: a leaderboard entry can
theoretically show without a username in the (sub-second, in practice)
window between an `exercise.completed` event and that user's
`user.registered` event being processed. That's accepted because the
alternative — reaching across a service boundary through a database table
just because the database happens to be shared infrastructure — defeats
the entire point of the boundary. Idempotency follows the same
"natural key, not a ledger" philosophy: a `daily_activity(user_id,
activity_date)` table with a UNIQUE constraint absorbs both same-day
multiple completions and Kafka at-least-once redelivery via
`ON CONFLICT DO NOTHING`, with no separate dedup table needed — verified
live by hard-killing the `event-worker` container mid-flow (`docker kill`,
2026-07-20) and confirming no duplicate state after it rejoined the
consumer group.

## 6. Security model

Course/lesson mutation (`POST`/`PUT`/`DELETE`) requires an `ADMIN` role — a
real gap that existed from M1 (2026-07-14) until M5 (2026-07-21): those
endpoints have had full CRUD since the very first milestone, but
`SecurityConfig` only ever checked `.authenticated()`, so any registered
user could mutate any course or lesson through four subsequent milestones
of work before this was caught and fixed.

The role is **re-derived from the database on every request**, not
carried in the JWT. `JwtAuthFilter` already calls
`userDetailsService.loadUserByUsername(...)` per request (a fresh
`UserRepository.findByEmail` lookup), so adding a role check meant zero
authentication code changes — `UserDetailsServiceImpl` just stopped
hardcoding `.roles("USER")` for every principal. A JWT role claim was
considered and rejected: it would be redundant (authorities already come
from a live lookup) and would introduce a real staleness risk — a demoted
admin's already-issued token would keep asserting the old role until
expiry if anything ever trusted the claim over the database.

No API path can create or promote an admin. Self-registration always
defaults to `USER`; the only way to become an admin is a direct
`UPDATE users SET role = 'ADMIN' WHERE email = '...'`, run by whoever
operates the database — the same trust boundary already used for
`core-api-secret` and the Cloudflare Origin CA TLS secret, both created
directly on the cluster, never through a checked-in bootstrap script or
admin-invite endpoint.

## 7. Observability

Metrics and traces export **directly from each service to Grafana Cloud's
OTLP endpoint** — no Grafana Agent/Alloy, no self-hosted Collector,
no in-cluster Prometheus/Grafana at all. On a resource-constrained Oracle
free-tier node, that's not a stylistic preference: every additional pod
is real RAM a portfolio-scale demo doesn't have to spend. core-api already
had the tracing dependencies since M1, but they'd never actually worked —
the default OTLP endpoint resolved to `localhost`, which inside a
container means the container itself, so every export attempt had failed
silently since day one.

Two genuinely non-obvious things had to be learned by measuring against
real endpoints, not by reading docs once and trusting them:

- **Spring's OTLP endpoint property wants the full path
  (`/v1/traces`); the native Python/Go OTel SDKs want the bare base URL
  and append the path themselves.** This exact mismatch was hit twice —
  once against local Jaeger, again against Grafana Cloud — because it's a
  genuine convention difference between Micrometer's Spring Boot
  integration and the vanilla OTel SDKs, not something either side's docs
  makes obvious up front.
- **Kafka trace propagation needed exactly one property**
  (`spring.kafka.template.observation-enabled: true`) on the producer side
  — verified against Spring Kafka's own `sample-08` before writing it. On
  the Go consumer side, there's no off-the-shelf `kafka-go` OTel
  instrumentation the way there is for HTTP, so `event-worker` extracts
  `traceparent`/`tracestate` from Kafka message headers by hand.

## 8. What I'd do differently at scale

Not a hedge — these are the specific cuts this project already made
explicitly, each with a real trigger that would justify revisiting it:

- **A transactional outbox**, once event delivery actually needs a
  guarantee stronger than "best-effort, logged on failure" — the moment a
  dropped `exercise.completed` event has a real cost (e.g., a streak that
  silently never updates) rather than being invisible.
- **A genuinely separate database per service**, once `event-worker`'s
  shared-instance-but-separate-schema arrangement stops being "good
  enough" — the free-tier constraint that justified one Postgres instance
  goes away, and so does the reason to keep sharing it.
- **Provider failover with circuit breakers and health tracking**, instead
  of "try each provider once, every time, from the top." The current chain
  re-attempts a known-bad provider on every single request; a stateful
  circuit breaker would skip it for a cooldown window once it's seen fail
  repeatedly — worth the added complexity once request volume makes
  repeatedly eating a provider's timeout costly rather than incidental.
- **A frontend admin UI**, once content authoring happens often enough
  that "an admin edits courses/lessons via curl" (the actual state today —
  `admin-content-authoring` added the authorization boundary, not a UI)
  stops being fine.
