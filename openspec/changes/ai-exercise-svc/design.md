## Context

`add-exercise-domain` (archived) gave core-api `Exercise`/`ExerciseAttempt`
JPA entities, `ExerciseService.listByLesson`/`submitAttempt`, and Flyway-seeded
placeholder content for 2 lessons. `ExerciseService.listByLesson` currently
does one thing: `exerciseRepository.findByLessonId(lessonId)`. `Lesson` has
`content: String` (TEXT) and `courseId`; `Course` has `jlptLevel` — enough
context to hand a provider for generation. Grading (`ExerciseService.grade`),
attempt persistence, and progress completion via
`ProgressService.markComplete(..., CompletionSource.EXERCISE)` are unchanged
by this proposal and must keep working identically regardless of whether an
`Exercise` row came from Flyway or from generation.

## Goals / Non-Goals

**Goals:**
- A lesson with no `Exercise` rows gets exercises generated on first request,
  persisted, and served — transparent to the caller (`ExerciseController`
  and the frontend see no difference between seeded and generated content).
- Every LLM response is validated against a strict JSON schema before
  anything is persisted; schema mismatches fail loudly (5xx), never silently
  produce malformed `Exercise` rows.
- A lesson is only ever sent to the LLM once. MongoDB caches the raw
  generation result keyed by lesson id, so a second request for the same
  lesson (e.g., after a core-api restart with an empty `exercises` table
  filtered by some future reset, or a cache-warm call) reads Mongo instead
  of re-calling the provider.
- Provider key material never enters the repo or this conversation — same
  external-file convention already used for `~/.config/dev-projects/llm-keys.env`.

**Non-Goals:**
- Multi-provider failover (M3 step 3, separate future change).
- Frontend UI for generated exercises (M3 step 4).
- Regenerating or invalidating cached/persisted exercises — this slice is
  generate-once, no admin/refresh endpoint.
- Streaming responses, personalization, or difficulty adaptation.
- `ai-exercise-svc` never touches Postgres directly — it has no core-api
  credentials and no ORM; core-api is the only writer of `Exercise` rows.

## Decisions

**One provider: Gemini, called via `google-genai`.**
Gemini's structured output mode (`response_schema`) enforces the JSON shape
at the provider level, which is a stronger guarantee than parsing free-form
text from providers without native schema support — a good fit for "validate
strictly, fail loudly, never persist garbage." Groq and OpenRouter keys exist
but are unused until the M3 step-3 failover change; picking Gemini now means
that future change adds providers around an interface this slice already
defines, rather than needing a retrofit.

**Request/response, not push.** `ai-exercise-svc` exposes
`POST /generate` (or similar) taking `{ lessonId, content, jlptLevel }` and
returning validated exercises in the response body. core-api calls it
synchronously from `ExerciseService.listByLesson` when `findByLessonId`
returns empty, persists the response as `Exercise` rows via the existing
repository, then proceeds exactly as today. Rejected alternative: an
async/event-driven flow (`ai-exercise-svc` consumes a Kafka event and later
writes to Postgres itself) — rejected because it would require giving a
Python service direct Postgres write access (breaks the "core-api is the only
writer" boundary) and because the roadmap's user-facing flow
("request a lesson → get an exercise") is inherently synchronous; Kafka stays
reserved for the existing best-effort side-effect events.

**MongoDB cache keyed by `lessonId`.** A single document per lesson holding
the generated exercise set (MCQ + fill-in-blank per the existing
`ExerciseType`). On a `/generate` call, `ai-exercise-svc` checks Mongo first;
a hit returns cached data with no provider call. A miss calls Gemini,
validates, writes to Mongo, then returns. This bounds LLM spend to one call
per lesson ever (for this slice — no invalidation).

**Schema validation via Pydantic models mirroring `ExerciseType`.** The
FastAPI response model (and the schema passed to Gemini's structured-output
config) mirrors `ExerciseDtos.kt`'s shape: `type` (`MCQ` | `FILL_IN_BLANK`),
`prompt`, `options` (required for MCQ, absent for fill-in-blank),
`correctAnswer`. Pydantic validation on the provider response is the "strict
JSON-schema validation" from the proposal; a `ValidationError` is caught and
surfaced as a 502 to core-api (generation failure), never partially applied.

**core-api → ai-exercise-svc call is synchronous HTTP, not best-effort.**
Unlike Kafka publishing, this call is on the request's critical path (the
user is waiting for exercises to display) — no try/catch-and-continue. A
failure here is a real 5xx to the frontend, which is correct: there's no
sensible "keep going without exercises" fallback for a request whose entire
purpose is fetching exercises. A reasonable timeout (e.g. 15s, generation
can be slow) is set on core-api's HTTP client to avoid indefinitely hanging
the user-facing request.

**Secrets**: `ai-exercise-svc` reads `GEMINI_API_KEY` from its process
environment. Local dev: `env_file: /home/ando/.config/dev-projects/llm-keys.env`
in `docker-compose.yml` (absolute path, outside repo, never copied in).
Production: a `kubectl create secret generic ai-exercise-secret
--from-env-file=<external path>` step, deferred to when this service is
actually deployed (not a task in this change — mirrors how `core-api-secret`
was created directly on the cluster, not scripted into the repo).

## Risks / Trade-offs

- [Gemini rate limits / latency on first request per lesson] → Mongo cache
  means the cost is paid once per lesson; acceptable for a portfolio demo
  with a small, fixed lesson set.
- [Synchronous call-out adds a new failure mode to a previously all-Postgres
  read path] → scoped deliberately (see Decisions) since there's no
  meaningful degraded response for "list exercises" when none exist yet;
  documented as a known trade-off rather than papered over with a fallback.
- [No cache invalidation] → acceptable for this slice (Non-Goals); flagged
  as a known cut, revisit if lesson content is ever edited post-generation.
- [MongoDB is a new piece of infra to run/monitor] → single-node, same
  docker-compose/k8s pattern already used for postgres/kafka; no clustering
  or replication for a portfolio-scale workload.

## Migration Plan

- New service, new Mongo instance — no migration of existing data. Existing
  Flyway-seeded `Exercise` rows for the 2 seeded lessons are untouched (their
  lessons already have rows, so `listByLesson` never calls out for them);
  generation only triggers for lessons with zero `Exercise` rows.
- Rollout: deploy `ai-exercise-svc` + Mongo to the cluster, wire core-api's
  new HTTP client config (base URL via env var, same pattern as
  `KAFKA_BOOTSTRAP_SERVERS`), roll out core-api. Rollback: revert core-api's
  image tag (sha-pin escape hatch, per existing runbook) — `ai-exercise-svc`
  becoming unreachable only affects lessons with no exercises yet, not the
  already-seeded ones.

## Open Questions

- Exactly which lessons/content get generation first in practice (all N5
  lessons beyond the 2 seeded, or a broader set) is a content decision, not
  an architectural one — left to whoever seeds/extends lesson content, not
  blocking this change.
