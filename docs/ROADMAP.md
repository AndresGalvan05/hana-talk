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
| M5 — Polish (tracing, dashboards, admin role, docs) | ✅ Done 2026-07-21 — tracing, admin role, Grafana Cloud dashboards, architecture doc + demo script. **Roadmap complete.** |

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

**Order inside milestone** (split like M3, since Grafana Cloud needs an
external account):
1. ✅ **Done 2026-07-21** (`cross-service-tracing`): a local Jaeger
   collector plus real, working trace propagation across every service
   boundary — core-api → Kafka → `event-worker` (W3C `traceparent` in
   record headers, `spring.kafka.template.observation-enabled`), and
   core-api → `ai-exercise-svc`/`event-worker` over HTTP. Found and fixed
   a real bug along the way: `AiExerciseSvcClient`/`EventWorkerClient`
   both built their `RestClient` via the static `RestClient.builder()`
   factory, which — per Spring Boot's own docs — silently skips all
   auto-configuration, including trace-header injection; fixed by
   injecting the Spring-managed `RestClient.Builder` bean instead.
   Verified live: a single trace in Jaeger spans core-api's HTTP request,
   its Kafka publish, and `event-worker`'s consumption of that message;
   separately, core-api's calls to `ai-exercise-svc` and `event-worker`'s
   internal API each produce a correlated two-service trace.
2. ✅ **Done 2026-07-21** (`grafana-cloud-observability`): core-api ships
   metrics (new `micrometer-registry-otlp`) and traces directly to Grafana
   Cloud's OTLP endpoint — no in-cluster Prometheus/Grafana/Agent, the
   actual RAM-saving story. Scoped to core-api only: `ai-exercise-svc`/
   `event-worker` were never deployed to production (`infra/k8s/` has no
   manifests for them), so wiring their observability is separate future
   work. Verified live against the user's real Grafana Cloud stack (not
   mocked) via the same external-secrets-file pattern as the LLM keys —
   metrics, traces, and all four dashboard panels (request rate, error
   rate, JVM memory, Kafka publish rate) confirmed with real data,
   including a deliberately-triggered 502 to confirm the error-rate panel.
   Two real bugs hit and fixed during verification, not assumed away: the
   OTLP endpoint path convention (Spring wants the full `/v1/traces` path;
   the wizard-provided value was the base URL) and a Grafana Cloud access
   policy missing the `traces:write` scope. Production rollout is
   documented (`infra/k8s/README.md` §12), not automated — the user
   applies it against the live cluster themselves.
3. ✅ **Done 2026-07-21** (`admin-content-authoring`): closed a real,
   live security gap — `CourseController`/`LessonController` have had full
   POST/PUT/DELETE since M1, but `SecurityConfig` only ever checked
   `.authenticated()`, so any registered user could mutate any course or
   lesson. `User` gained a `role` (`USER`/`ADMIN`, defaulting to `USER`);
   `UserDetailsServiceImpl` looks it up fresh from the database on every
   request (no JWT role claim — deliberately rejected, since
   `JwtAuthFilter` already re-derives `UserDetails` per request);
   `SecurityConfig` gained `hasRole("ADMIN")` matchers for course/lesson
   mutation only, reads untouched. No API path can create or promote an
   admin — that's a direct SQL `UPDATE` an operator runs, the same trust
   boundary already used for `core-api-secret`. Verified live: a fresh
   user defaults to `USER` and gets 403 on course creation; after a manual
   SQL promotion to `ADMIN`, the same token can create/update/delete
   courses and lessons; all GET endpoints work identically regardless of
   role, exactly as before.
4. ✅ **Done 2026-07-21** (`architecture-doc-and-demo-script`): new
   `docs/ARCHITECTURE.md` — a synthesized narrative (not new design work)
   built from the decision log below and `docs/DEVLOG.md`'s session
   history, organized around 8 questions an interviewer would actually
   ask (sync/async boundary, the outbox trade-off, cross-service ownership,
   security, observability, "what I'd do differently at scale"). New
   `docs/DEMO_SCRIPT.md` — a literal timed ~2-minute walkthrough, scoped
   to the local docker-compose stack since production doesn't have
   `ai-exercise-svc`/`event-worker` deployed. Also fixed real `README.md`
   staleness found while writing both: roadmap checkmarks, the admin-role
   and LLM-failover sections were both still describing shipped work as
   planned/future. **This completes the roadmap — M1 through M5 are all
   done.**

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
| 2026-07-21 | M5 split like M3: `cross-service-tracing` proposed and shipped first (unblocked); Grafana Cloud dashboards deferred as a separate change pending an external account/API key. Local Jaeger (`all-in-one`) chosen over a bare OTel Collector purely for local verification — not a production decision. |
| 2026-07-21 | User created a Grafana Cloud account, clearing that M5 blocker. `admin-content-authoring` proposed next instead (still unblocked, and closes a real security gap already live since M1). No JWT `role` claim added — `JwtAuthFilter` already re-derives `UserDetails` from the database on every request, so role is always current without one; a claim would only add staleness risk. No API path can create/promote an admin — a direct SQL `UPDATE`, matching how `core-api-secret` was created directly on the cluster rather than through a checked-in bootstrap path. |
| 2026-07-21 | `grafana-cloud-observability` scoped to core-api only after discovering `ai-exercise-svc`/`event-worker` were never deployed to production (no `infra/k8s/` manifests exist for them) — deploying them is separate future work, decided explicitly with the user rather than silently assumed. Direct OTLP export from core-api chosen over a Grafana Alloy/Collector agent specifically to avoid any new in-cluster component (the actual "saves cluster RAM" point). During live verification against the real Grafana Cloud stack: hit the same endpoint-path convention mismatch as the local Jaeger work (Spring wants the full `/v1/traces` path, Grafana's wizard gives the base URL) and separately a Grafana Cloud access-policy scope gap (`invalid scope requested` — needed both `metrics:write` and `traces:write`). Confirmed real Micrometer-OTLP metric names live (`_milliseconds_count` suffix, not `_seconds_count`) rather than guessing, and fixed the dashboard JSON to match. |
| 2026-07-21 | `architecture-doc-and-demo-script`: the demo script is scoped to the local docker-compose stack, not the live site, after confirming production is missing `ai-exercise-svc`/`event-worker` — asked the user explicitly rather than silently picking a scope, since it changes the entire deliverable's shape. `docs/ARCHITECTURE.md` is framed as a synthesis of decisions already made and dated in this log, not new design work. **Roadmap complete — M1 through M5 all done.** |
| 2026-07-27 | Closed the "never deployed to production" gap `grafana-cloud-observability` and `architecture-doc-and-demo-script` both flagged: `ai-exercise-svc`, `event-worker`, and MongoDB are now live on the k3s cluster, all secrets created directly against the cluster (never read by the assistant). Found and fixed three real bugs surfaced only by actually deploying: a multi-arch Docker build bug that shipped amd64 binaries under event-worker's arm64 manifest tag, a DB credential broken by unescaped k8s `$(VAR)` string interpolation in its connection URL, and a truncated Grafana Cloud credential in the external secrets file. `docs/DEMO_SCRIPT.md`'s local-compose scoping note is now stale — re-scoping it to the live site is a natural next step, not done here. See DEVLOG 2026-07-27 for full detail. |
| 2026-07-27 | `expand-n5-curriculum-genki`: first post-roadmap change (M1–M5 already complete) — content depth, not new engineering. N5 course expanded 5→10 lessons, using Genki I's topic sequence as the structuring reference rather than an ad-hoc curriculum. Delivered via a Flyway migration rather than the newly-available `admin-content-authoring` CRUD API, specifically so every fresh environment (not just production) gets the content from first boot. Explicit cut line: Genki I lessons 6–12 and all of Genki II (N4) stay out of scope — a 10-lesson N5 course is the target, not a full textbook port. |
| 2026-07-29 | User judged the expanded 10-lesson course still "very lacking and short" and the product "superficial" — correctly: the real problem was `Lesson.content` being one flat-text field with no structural concept of vocabulary/grammar-point/dialogue, not word count. `structured-lesson-content` replaced the model and consolidated to 5 chapter-depth lessons; planned as the first of five slices (structured content, new exercise types, AI conversation practice, vocabulary flashcards/spaced review, audio pronunciation) after the user picked all four beyond the content rework itself. Only slice 1 proposed and built so far — later slices get their own `/opsx:propose` in turn. Vocabulary deliberately became a real table (not JSON) specifically to avoid a breaking migration when the spaced-repetition slice lands. Along the way: archived `expand-n5-curriculum-genki` (had shipped 2026-07-27 but was never archived) so its `curriculum-content` capability existed as a real base spec to write this change's delta against. |
| 2026-07-29 | `new-exercise-types` (slice 2): added `TRANSLATION` and `SENTENCE_ORDERING`, reusing `Exercise.optionsJson`/`correctAnswer` for the new sentence-ordering shape rather than a new column. Chose *not* to hard-require the two new types in every generated batch (still just ≥1 MCQ, ≥1 fill-in-blank, ≥4 total) — prompted for via guidance instead, to avoid making generation more failure-prone on weaker fallback providers for lessons with fewer grammar points. Verified live that prompt guidance alone was enough to get a genuine 4-type mix without a hard floor. |
| 2026-07-30 | Evaluated a former colleague's suggestion to adopt CQRS + event sourcing. Verdict: CQRS's actual benefit (lean, purpose-built read models instead of loading a full aggregate for a query) is already in production — `event-worker`'s streak/leaderboard tables are exactly that, fed by `user.registered`/`exercise.completed`. Declined full event sourcing (events as the system of record, state derived by replay) as disproportionate to this domain — no temporal/audit query need, no read/write divergence severe enough to justify permanent event-schema compatibility and snapshotting. Documented as `docs/ARCHITECTURE.md` §6. Separately, audited actual usage of the two existing Kafka topics and found the backend work (leaderboard, streak) has **zero frontend surface** — not superficial plumbing, just unshipped UI on top of it. New slice **`profile-and-progress`** planned next (streak badge, leaderboard page, JLPT-level setting — the last already has a working, unused `PATCH /api/users/me/level` endpoint) to surface that existing work before starting slice 3 (AI conversation practice), since it's cheap and converts already-deployed backend investment into something demoable. A lesson prev/next + course-index UI gap in `LessonPage` was also found (only a "back to course" link exists today) — small enough to fix directly, no proposal needed. An achievement/milestone system reacting to streak/completion thresholds (real multi-hop event choreography, not just flat projection) was discussed as a possible later slice, lower priority than the above. |
| 2026-07-30 | `profile-and-progress`: shipped as pure frontend work, zero backend changes — every endpoint it needed (`GET /api/users/me`, `PATCH /api/users/me/level`, `GET /api/users/me/streak`, `GET /api/leaderboard`) already existed and was already live. New `ProfilePage`/`LeaderboardPage`; the leaderboard highlights the signed-in user's own row by matching `username` (the only identity already available client-side via `useAuth()`), not `userId`. Verification hit a local-only snag: `event-worker`'s Kafka consumer group got stuck rebalancing in this session's docker-compose stack and never processed real events; worked around it by seeding a streak row directly in the local `event_worker` schema to verify the frontend rendering, since debugging the consumer was out of scope for a frontend-only change. Not a production concern — see DEVLOG for detail. |
| 2026-07-30 | `ai-conversation-practice` (slice 3): a `/chat` page for free-form Japanese conversation with an LLM tutor, with corrections — the first non-closed-form practice surface in the app. `ai-exercise-svc`'s provider-fallback loop was extracted into a shared `call_with_fallback` helper (a `parse` callback preserves the existing schema-failure-falls-through-too behavior) and reused for a new `/chat` endpoint; core-api added `getChatReply` to the existing `AiExerciseSvcClient` rather than a new client bean, and derives the JLPT level server-side from `user.startingLevel` rather than trusting the request. Zero persistence — conversation history is frontend-React-state-only for the page's lifetime, per the original slice plan. Found and fixed a real prompt-quality gap while verifying: "keep Japanese at or below {jlpt_level}" doesn't push toward harder language at the top of the scale (N1), so replies stayed simple regardless of level, until the prompt was rewritten with concrete per-level example sentences. Full writeup in DEVLOG, including a UI/automation false alarm (a level-save click that silently didn't persist) that had to be ruled out via a direct DB check before trusting the prompt comparison. |
| 2026-07-30 | `vocabulary-review` (slice 4): a `/flashcards` page with Leitner-style spaced repetition (correct doubles the interval, capped at 90 days; incorrect resets to 1 day), scoped to vocabulary from lessons the user has completed rather than the entire catalog. New `user_vocabulary_progress` table (`V13`) — the first genuinely mutable per-user table in the codebase, every prior one being an append-only log. Pure core-api + frontend work, no `ai-exercise-svc`/Kafka involvement. Caught a mockito-kotlin `any()` pitfall (an unparameterized `any()` against a generic `save()` silently failed to stub, causing every review to NPE) and fixed it with an explicit type parameter, matching an existing working precedent elsewhere in the test suite. Verified the scheduling math against the database directly rather than trusting the UI, continuing the discipline established in the previous slice. |
| 2026-07-30 | `audio-pronunciation` (slice 5, the last slice): a 🔊 speaker button on vocabulary rows, grammar-point examples, and flashcards, using the browser's built-in `speechSynthesis` API — no backend, no new dependency. One shared `speak()`/`SpeakButton` used in all three places; always cancels any in-progress utterance before starting a new one. This completes the five-slice deepening-interactivity plan from `structured-lesson-content`'s proposal, plus the interleaved `profile-and-progress` slice that wasn't in the original plan. Verification hit a real environment gap, not a code bug: this dev sandbox has zero TTS voices installed (`speechSynthesis.getVoices().length === 0`), so actual audio playback couldn't be confirmed locally — verified correctness at the API level instead (calling `speak()` throws nothing and carries the right `lang`/`rate`, and monkey-patching `cancel` confirmed it fires exactly once per click) and deferred the "can you actually hear it" check to the production spot-check. |
| 2026-07-31 | With all five deepening-interactivity slices plus `profile-and-progress` shipped, planned what's next: rate limiting, an achievement/milestone system, an admin UI for content authoring's existing API, and extending observability to `ai-exercise-svc`/`event-worker` (currently core-api-only per the `grafana-cloud-observability` scoping decision). User picked rate limiting first — the chat endpoint was the one AI-backed surface with no cap. |
| 2026-07-31 | `chat-rate-limiting`: 10 requests/minute per authenticated user on `/api/conversation/reply`, via a new in-memory `RateLimiter` (fixed-window, per-key synchronized, injected `Clock` for testability) called inline from `ConversationController` rather than a `Filter` — one call site doesn't justify a generic framework. No Redis: single replica, in-memory state is sufficient. Exercise generation stayed out of scope — already self-limiting via `ExerciseService.listByLesson`'s permanent per-lesson cache. Sequential curl testing initially looked broken (11 requests, all 200) until realizing real chat calls take 6-30+ seconds, so sequential requests span well past the 60s window before it can fill up; concurrent requests confirmed the limiter works correctly, both locally and again against production after deploy. |
| 2026-08-01 | `achievement-system`: second post-roadmap slice, chosen over an admin UI and extended observability for having genuine new architectural texture (derived, multi-hop state from existing events) rather than being hardening work on an existing surface. `event-worker` evaluates a fixed 6-achievement catalog (streak + lesson-completion milestones) inline off the existing `exercise.completed` handling — no new Kafka topic or consumer. Found and fixed a real correctness gap while designing, before writing any code: the existing day-granularity activity guard used for streak updates can't also gate completion counting, since a second distinct lesson completed on an already-active day would be silently dropped — fixed with a separate `lesson_completions` idempotency table keyed on `(user_id, lesson_id)`. Verified directly (not just reasoned about): same-day multi-lesson completions all counted, and a manually-redelivered Kafka message didn't double-count. Hit the same local-only Kafka consumer-group flakiness `profile-and-progress` recorded — resolved by restarting `event-worker` locally; production processed the same flow immediately with no such issue. |
| 2026-08-01 | `admin-content-ui`: third and last of the "close a known gap" post-roadmap slices before extended observability. `admin-content-authoring` had shipped a role + full course/lesson CRUD API with no frontend ever built for it — content could only be authored via curl. `GET /api/users/me` now exposes `role`, fetched once after login and persisted client-side (no JWT change). Explicit scope decision made in the proposal, before any code: lesson content (grammar points, dialogue, culture note) is edited as a JSON textarea with client-side parse validation, not a bespoke nested-list form builder — chosen because a full add/remove/reorder editor for that shape would be the largest UI surface in the app for content only the admin ever touches. Vocabulary CRUD stayed explicitly out of scope too, since the vocabulary API is read-only today and adding mutation endpoints is separate backend work. First non-native delete confirmation in the app (inline Confirm/Cancel) chosen over `window.confirm()` specifically to stay scriptable for browser-automation verification. Two rounds of browser-automation click-timing flakiness during verification (stale role read after a rushed logout/login; a malformed-JSON test racing an async content-load) were both confirmed as test artifacts, not app bugs, by cross-checking the actual API/network behavior directly rather than trusting the UI. |
| 2026-08-02 | `responsive-mobile-ui`: first frontend slice driven directly by user feedback rather than a pre-planned post-roadmap item. `index.css` had zero `@media` queries across ~755 lines and the header nav had grown to 7 items with no wrap — confirmed broken, not hypothetical, before writing a design doc. Added a hamburger + slide-out drawer below a single 640px breakpoint, plus mobile-safe fixes for lesson prev/next, admin/leaderboard rows, and the vocabulary table; achievement cards also gained a lock/unlock icon (the "visualization" part of the request, scoped to polish, not new charts, after asking the user directly). The browser-automation tool's `resize_window` didn't actually resize the OS window in this environment (likely the tiling window manager) — verified instead via a same-origin iframe fixed at a target CSS width, which correctly triggers real `@media` evaluation. Found and fixed a real CSS bug during that verification: an unrelated `grid-column: 2` rule on the "next lesson" link was forcing CSS Grid to keep a second implicit column alive even after the container's explicit template collapsed to one track, so the initial one-line mobile override silently didn't stack the layout — fixed by also resetting that child's `grid-column` inside the same media block. Verified identically in both local and production after deploy. |
| 2026-08-06 | `service-metrics-export`: the last item from the post-roadmap planning session's original list ("extended observability"), but research before writing the proposal corrected the assumed scope — tracing for `ai-exercise-svc`/`event-worker` was already live in Grafana Cloud (shipped by `cross-service-tracing`), so the real gap was metrics only. Confirmed with the user: metrics export only, no new dashboards, focused on LLM call latency/failure per provider, Kafka processing throughput/errors per topic, plus standard HTTP metrics that come free from already-present instrumentation libraries. Export gated behind a new `OTEL_METRICS_ENABLED` flag (default off), mirroring core-api's existing `GRAFANA_CLOUD_METRICS_ENABLED` pattern, since local Jaeger only accepts OTLP traces. Found and fixed two real bugs during implementation, before either could reach production: a circular import the design doc's plan would have introduced in `ai-exercise-svc` (fixed by reading the meter from OTel's global registry instead of importing it from `app.main`), and a provider-naming approach that would have silently broken existing test monkeypatching (a dict keyed by function identity doesn't survive a patched callable's identity changing; fixed with plain `(name, callable)` tuples). Also found a real Go OTel SDK constraint while testing `event-worker`: the global MeterProvider only upgrades already-created instruments the *first* time it's registered process-wide, so two separate tests each calling `SetMeterProvider` silently lost data in the second one — fixed by combining both assertions into one test. Verified the exporter fails non-fatally against local Jaeger for both services (waited past the export interval, confirmed no crash). Could not personally verify the Grafana Cloud Explore side of the export (credentials live outside the repo, deliberately not read) — flagged as a follow-up check for the user after triggering real production activity against both instrumented code paths. |
| 2026-08-06 | `service-metrics-export` follow-up: the user shared their own Grafana Cloud Explore URL, letting the deferred verification finally happen directly. `ai-exercise-svc`'s metrics were live and correctly labeled; `event-worker` had zero metrics of any kind. Root cause: `event-worker/deployment.yaml` wires every env var individually via `configMapKeyRef`, unlike `ai-exercise-svc/deployment.yaml`'s blanket `envFrom` — adding `OTEL_METRICS_ENABLED` to `event-worker-config`'s data never actually reached the pod, confirmed via `kubectl exec ... -- env`. The two services' env-wiring conventions were never actually the same, and nothing in the original implementation checked that assumption. Fixed by adding the missing explicit `env:` entry, applied and restarted; confirmed via a fresh production request producing `kafka_message_processing_duration_seconds` data in Explore within the next export cycle. Lesson: "the ConfigMap has the key" and "the pod's environment has the key" are different facts here, and a config-wiring task should say which convention a service actually uses rather than assuming one description covers both. |
