## Why

M3 slice 1 (`add-exercise-domain`, archived 2026-07-20) built grading, attempts,
and progress integration in core-api, but exercises are Flyway-seeded
placeholders — every user sees the same 4 hand-written questions. The
interview story for M3 is a polyglot sync boundary: a Kotlin gateway calling a
Python/FastAPI service that talks to an LLM provider. LLM API keys (Groq,
Gemini, OpenRouter) are now procured, unblocking this slice.

## What Changes

- New `ai-exercise-svc`: Python + FastAPI service with a single synchronous
  endpoint that, given a lesson's content and JLPT level, calls **one** LLM
  provider (Gemini — native structured/JSON-schema output fits the strict
  validation requirement below) and returns generated exercises in the same
  shape core-api already persists (MCQ / fill-in-blank, matching
  `ExerciseType`).
- Every provider response is validated against a strict JSON schema before
  it's returned; a response that fails validation is treated as a generation
  failure (5xx to the caller), never passed through partially.
- Generated exercises are cached in MongoDB keyed by lesson id (+ type), so a
  lesson is only sent to the LLM once; repeat requests for the same lesson
  are served from the cache with no provider call.
- core-api's `ExerciseService.listByLesson` calls `ai-exercise-svc` when a
  lesson has no `Exercise` rows yet, persists what comes back as ordinary
  `Exercise` entities via the existing repository, then serves the request
  exactly as it does today for seeded content. No changes to the grading,
  attempt, or progress-completion path from `add-exercise-domain` — those
  requirements are reused unchanged, just against LLM-sourced data instead of
  Flyway-seeded data.
- Local dev wiring: a new `ai-exercise-svc` entry in `infra/docker-compose.yml`
  plus a MongoDB service, with `env_file: ~/.config/dev-projects/llm-keys.env`
  (absolute path outside the repo — the file is never copied in or read by
  tooling that isn't the container runtime itself).
- Production wiring: a k8s Secret for the provider key created out-of-band via
  `kubectl create secret generic ai-exercise-secret --from-env-file=<external
  path>` (not part of this change's tasks — deferred until this service is
  ready to deploy, per the existing convention of not creating secrets for
  deployments that don't exist yet).

## Capabilities

### New Capabilities
- `exercise-generation`: given a lesson with no existing exercises, generate
  MCQ/fill-in-blank exercises via one LLM provider, validate strictly against
  a JSON schema, cache the result in MongoDB, and hand validated exercises to
  core-api for persistence as ordinary `Exercise` rows.

### Modified Capabilities
(none — `exercise-grading`'s requirements, from `add-exercise-domain`, are
reused unchanged; only the source of the `Exercise` rows changes, which is an
implementation detail, not a spec-level behavior change)

## Impact

- **New service**: `ai-exercise-svc/` (Python, FastAPI, MongoDB client, one
  LLM provider SDK).
- **core-api**: `ExerciseService.listByLesson` gains a call-out-if-empty path
  and an HTTP client for `ai-exercise-svc`; no changes to `ExerciseController`,
  grading, or `ProgressService` integration.
- **infra**: `docker-compose.yml` gains `ai-exercise-svc` + `mongo` services;
  new Dockerfile + CI workflow for `ai-exercise-svc` (mirrors `core-api.yml`
  structure: build, lint, test, push to GHCR).
- **Non-goals / cut line**: no failover across providers (single provider only
  — failover is M3 step 3, a separate future change with simulated-failure
  tests); no frontend exercise UI (M3 step 4, deferred until this service
  produces real content to build against); no personalization, spaced
  repetition, or streaming generation; no admin/regeneration endpoint (cache
  is permanent for this slice — invalidation is a future concern if it comes
  up).
- **Milestone**: M3, step 2 of `docs/ROADMAP.md`'s "Order inside milestone".
