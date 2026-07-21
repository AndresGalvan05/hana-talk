# HanaTalk Roadmap

Milestones, not calendar weeks. Each has a goal, the interview story it unlocks,
and an explicit cut line. Approved 2026-07-13.

## Status at a glance

| Milestone | Status |
|---|---|
| M1 — Vertical slice (frontend on existing API) | ✅ Done 2026-07-14 |
| M1.5 — Adopt spec-driven development (OpenSpec) | ✅ Done 2026-07-14 |
| M2 — Deployed & public (k3s on Oracle) | ✅ Done 2026-07-19 — live at https://hanatalk.online |
| M3 — AI exercises (core-api domain + ai-exercise-svc) | ✅ Done 2026-07-20 — exercise domain, generation, provider failover, and frontend UI all shipped |
| M4 — Async side effects (Go event-worker) | ✅ Done 2026-07-20 — Kafka consumer, streaks, leaderboard |
| M5 — Polish (tracing, dashboards, admin role, docs) | Last |

## M1 — Vertical slice ✅

Register → login → browse seeded N5 course → read lessons → mark complete →
progress bar + per-lesson checkmarks. Delivered: CORS, V7 seed migration,
React/Vite/TS frontend, frontend CI, GHCR image push, plus two resilience fixes
found during verification (best-effort Kafka publishing, `/error` permitAll).
**Cut (deferred):** exercises, styling depth, admin UI, refresh tokens.

## M2 — Deployed & public ✅

**Goal:** M1 slice at hanatalk.online: k3s on Oracle Always Free, CI-pushed GHCR
images, Kafka in-cluster, Cloudflare TLS.
**Interview story:** solo CI→GHCR→k3s pipeline on free-tier infra.
**Delivered 2026-07-19:** multi-arch (amd64+arm64) images via buildx, single-node
k3s on Oracle A1 Flex (4 OCPU/24 GB, Oracle Linux 9, aarch64), postgres + Kafka
StatefulSets, Traefik ingress with Cloudflare Origin CA TLS (Full strict),
public GHCR packages, full E2E verified live incl. Kafka events, best-effort
publish with Kafka down, and deploy/rollback loop (sha-pin escape hatch).
See `infra/k8s/README.md` runbook and DEVLOG 2026-07-19.
**Cut (as planned):** GitOps/ArgoCD, autoscaling, in-cluster observability
(OTLP sampling 0 until M5).

## M3 — AI exercises

**Goal:** log in → get generated exercise for a lesson → answer → graded →
progress updates → `exercise.completed` with `source=EXERCISE`.
**Interview story:** polyglot sync boundary — Kotlin gateway calls Python/FastAPI
LLM service with provider failover + MongoDB cache; grading stays in the gateway.
**Needs from user:** LLM API keys (Groq / OpenRouter / Gemini free tiers) —
procured 2026-07-20, all three now in active use (see step 3).
**Order inside milestone:**
1. ✅ **Done 2026-07-20** (`add-exercise-domain`): exercise domain +
   attempts/grading in core-api — `Exercise`/`ExerciseAttempt` entities, MCQ +
   fill-in-blank graded synchronously against a stored answer (no LLM), a
   correct attempt reuses the existing `ProgressService.markComplete` with
   `CompletionSource.EXERCISE`, Flyway-seeded placeholder content for the N5
   lessons. See the `add-exercise-domain` OpenSpec change (archived once
   applied) for details.
2. ✅ **Done 2026-07-20** (`ai-exercise-svc`): new Python/FastAPI service,
   one provider (Gemini, via `google-genai`'s structured-output mode) +
   MongoDB cache keyed by lesson id + strict Pydantic schema validation.
   core-api's `ExerciseService.listByLesson` calls it synchronously
   whenever a lesson has zero `Exercise` rows, persists what comes back,
   and serves it identically to seeded content — verified live: real
   generation (~15s first call), cache hit on repeat (~ms), correct
   grading/progress/`exercise.completed` event, and no impact on the
   already-seeded lessons. See the `ai-exercise-svc` OpenSpec change
   (archived once applied) for details.
3. ✅ **Done 2026-07-20** (`provider-failover-chain`): `ai-exercise-svc` now
   tries Gemini → Groq (`openai/gpt-oss-20b`, strict JSON-schema mode) →
   OpenRouter (`google/gemma-4-26b-a4b-it:free`) in order, falling through
   on any failure — transport error or schema-validation failure — with
   simulated-failure unit tests covering every fallback path. core-api is
   unaware of the chain (unchanged). Verified live with a forced Gemini
   failure: Groq produced valid exercises; measuring that real failure
   path (~60s before falling through) led to raising core-api's
   `ai-exercise-svc.timeout-seconds` from 45s to 90s.
4. ✅ **Done 2026-07-20** (`exercise-practice-ui`): `LessonPage` gained a
   "Practice exercises" section — MCQ radio groups, fill-in-blank text
   inputs, per-exercise correct/incorrect feedback with immediate retry.
   A correct attempt reuses the exact same completion state/banner the
   manual "Mark as complete" button already drives, via a shared
   `refreshCompletion()` function — no duplicate completion UI. Verified
   live: first-time generation loading message upgrades after ~4s,
   completion banner appears without a page reload, exercises persist
   fast on reload, manual completion still works unchanged, and a forced
   `ai-exercise-svc` outage shows an error + retry that recovers cleanly
   once the service is back. **M3 is now fully done.**
**Cut:** streaming, personalization, spaced repetition, LLM-graded free text.

## M4 — Async side effects ✅

**Goal:** Go event-worker consuming `user.registered` / `exercise.completed`:
streaks (day granularity), leaderboard, internal read API proxied by the gateway
(`/api/leaderboard`, `/api/users/me/streak`).
**Interview story:** why Kafka and not REST — consumer groups, at-least-once,
idempotent handlers, idiomatic Go.
**Storage:** worker owns a separate schema in the shared Postgres (free-tier
reality, real ownership boundary).
**Delivered 2026-07-20:** new `event-worker` service (`kafka-go`, `pgx`,
stdlib `net/http` — no ORM, no router dependency), a fan-in consumer
(one goroutine per topic reader feeding a single DB-writing goroutine),
idempotency via a `daily_activity(user_id, activity_date)` UNIQUE
constraint rather than a separate dedup ledger, and a local `users`
projection built from `user.registered` (no cross-schema reads into
core-api's tables). core-api's `UserProfileController`/`LeaderboardController`
proxy the internal read API via a new `EventWorkerClient`. Verified live:
streak correctly reaches 1 after same-day completions collapse into one
activity row, leaderboard shows the username from event-worker's own
projection, and a hard-killed container recovers with no duplicate/
incorrect state after rejoining the consumer group. See the
`event-worker` OpenSpec change (archived once applied) for details.
**Cut:** notifications/emails, `streak.updated` topic, Redis.

## M5 — Polish

Cross-service OTel tracing (context through Kafka headers), Grafana Cloud
dashboards (saves cluster RAM), admin role for content CRUD, architecture &
trade-offs doc (incl. outbox-pattern discussion), 2-minute demo script.
**Cut:** load testing, chaos engineering, multi-env.

## Decision log

| Date | Decision |
|---|---|
| 2026-07-13 | Roadmap approved. Deploy at M2 (before exercises). Seed-migration-only content through M4; roles at M5. Frontend stack delegated (chose Vite + React 19 + TS, plain CSS, react-router). |
| 2026-07-13 | No Oracle VM exists; user creates it when M2 starts. No LLM keys; procure at M3. |
| 2026-07-14 | Playwright MCP server installed for browser verification (isolated browser; preferred over Chrome extension). |
| 2026-07-14 | M1.5 approved and done: OpenSpec adopted (chosen over Spec-Kit — brownfield delta specs, light ceremony; see DEVLOG for evaluation). From M2 on, milestone chunks run propose → apply → archive. CLI via npx, no global install. |
| 2026-07-19 | Oracle quietly halved Always Free A1 to 2 OCPU/12 GB (2026-06-15, no announcement). Our VM landed at the old 4/24 size — user to watch Cost Analysis + budget alert; stack fits in 2/12 if a resize is ever forced. |
| 2026-07-19 | VM shipped with Oracle Linux 9 (not planned Ubuntu). Kept: recreating risks losing the hard-won A1 capacity slot; k3s supports OL9 (k3s-selinux auto-installed, firewalld disabled per k3s docs — OCI VCN security list is the single firewall, 22/80/443 only). |
| 2026-07-19 | GHCR packages made public (repo is public; images hold only compiled artifacts; avoids imagePullSecret PAT rotation). Firebase considered and rejected as hosting alternative during the capacity drought — cannot run the fixed stack, would dissolve the k3s/Kafka story. |
| 2026-07-20 | M3 split into an exercise-domain slice (no LLM needed — grading is exact-match against seeded answers) and an ai-exercise-svc slice (needs keys), so the grading/progress plumbing didn't have to be designed under LLM-integration pressure. Frontend exercise UI deferred to the ai-exercise-svc slice rather than built against placeholder seed content. |
| 2026-07-20 | `ai-exercise-svc` picked Gemini as the single provider for this slice (native structured/JSON-schema output) over Groq/OpenRouter; both remaining keys are unused until the step-3 failover change. LLM keys stay outside the repo (`~/.config/dev-projects/llm-keys.env`) and are wired into `docker-compose.yml` via `${LLM_KEYS_ENV_PATH}` variable substitution from a gitignored `infra/.env`, never a hardcoded path in a tracked file. |
| 2026-07-20 | Groq/OpenRouter model IDs picked from live docs at implementation time rather than fixed in the design: `openai/gpt-oss-20b` (Groq's only strict-JSON-schema-capable model) and `google/gemma-4-26b-a4b-it:free` (OpenRouter, deliberately a different model family for infra diversity). `ai-exercise-svc.timeout-seconds` raised 45s → 90s after measuring a real forced-failover call. A key-redaction command mistake during live verification exposed the real Groq/OpenRouter key values in a tool-output file read into the session — both keys were rotated immediately; `GEMINI_API_KEY` was unaffected. |
| 2026-07-20 | `exercise-practice-ui` reuses `LessonPage`'s existing completion state (`completed`/`progress`) via a shared `refreshCompletion()` function rather than introducing a second completion UI — a correct exercise attempt and the manual "Mark as complete" button now drive the exact same banner. No frontend test runner exists in the repo, so verification stayed manual (Chrome browser automation), consistent with the rest of the frontend. M3 is fully done. |
| 2026-07-20 | Go toolchain installed on this machine for M4 (none previously). `event-worker`'s idempotency uses a `daily_activity(user_id, activity_date)` UNIQUE constraint instead of a separate dedup ledger — same-day duplicates and Kafka-redelivered events both collapse naturally. `event-worker` maintains its own `users` projection from `user.registered` rather than reading core-api's tables directly, even though both share one Postgres instance — an explicit ownership-boundary choice, not a technical limitation. |
