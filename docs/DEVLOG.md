# DEVLOG

Newest first. Every working session gets an entry: what shipped, what broke,
and root causes — so no lesson has to be relearned.

## 2026-07-29 — Structured lesson content: 10 shallow lessons → 5 real chapters

**Shipped**
- OpenSpec change `structured-lesson-content` — the response to the user
  calling the just-expanded 10-lesson course "still very lacking and short"
  and the site "superficial." The real gap wasn't word count: `Lesson.content`
  was a single flat-text column with no concept of "vocabulary," "grammar
  point," or "dialogue," so there was no structure to render depth into even
  if the text got longer. Replaced it with a genuinely structured model and
  consolidated to 5 chapter-depth lessons (mapped 1:1 to Genki I Lessons 1-5's
  topic scope), each with a vocab list, 7-8 grammar points with examples, an
  original dialogue, and a culture note. Content is original writing — the
  Genki I textbook, workbook, and teacher's manual (now under
  `reference-material/`, gitignored, carries an explicit "no scanning and
  uploading" copyright notice) were used only to check topic scope and
  grammar accuracy, never transcribed or paraphrased.
- `Lesson.content` → `Lesson.contentJson` (JSON string column, same pattern
  `Exercise.optionsJson` already used — not a new JSONB type, not new
  normalized tables for grammar/dialogue, since none of that needs SQL-level
  querying). Vocabulary is the one exception: a real `vocabulary_items`
  table, because a future spaced-repetition feature needs per-item,
  per-user review queries that are impossible against a JSON blob — decided
  now specifically so it doesn't need a breaking migration later. New
  `GET /api/lessons/{id}/vocabulary` endpoint (matches the existing
  top-level `/api/lessons/{id}/exercises` routing convention).
- `V9`'s seeded placeholder exercises are gone — every lesson now generates
  real exercises on demand, so the seed-exercise escape hatch that only
  ever existed because `ai-exercise-svc` didn't exist yet at M3 slice 1 is
  no longer needed. The 10 old lesson rows were replaced outright (not
  edited), which cascade-deleted their `user_lesson_progress`/`exercises`
  rows — accepted deliberately: only test/demo accounts had any progress at
  this stage, no real end users to lose data.
- `ai-exercise-svc`'s generation floor changed from a hardcoded "exactly 1
  MCQ + 1 fill-in-blank" to "at least 4 exercises, at least one of each
  type," driven by the lesson's actual `grammarPoints` list instead of flat
  text. Verified live against lesson 1 (7 grammar points): 9 exercises
  generated in one real call, covering nearly every point.
- Frontend: `LessonPage.tsx`'s single `<pre>` block replaced with four new
  components (`VocabularyTable`, `GrammarPointCard`, `DialogueBox`,
  `CultureNoteAside`). Verified live via Chrome automation — all four
  render correctly, and the completion banner correctly shows the course's
  new total (`1 / 5`).

**Errors & lessons**
- *A test-only Jackson gap, not a production bug*: `ExerciseServiceTest`
  constructs its own `ObjectMapper()` directly rather than getting Spring's
  auto-configured bean, which meant it lacked the `jackson-module-kotlin`
  registration Spring Boot auto-detects and wires up in the real app —
  deserializing the new `LessonContent` (a Kotlin data class tree) failed
  with `InvalidDefinitionException` in the test only. Confirmed it was
  test-only by checking for any custom `ObjectMapper` `@Bean` overriding
  Spring's auto-configuration (none exists) before concluding production
  was unaffected. Fixed with `.registerKotlinModule()` on the test's mapper.
- *Browser screenshots flaked mid-scroll during verification* (CDP
  `Page.captureScreenshot` timeouts) — switched to `get_page_text` to
  confirm the dialogue box and culture note rendered correctly instead of
  fighting the screenshot tool further; got a full, readable text dump of
  the rendered page in one call, which was actually more thorough
  verification than a screenshot would have been anyway.

## 2026-07-27 — N5 course expanded from 5 to 10 lessons, referencing Genki I

**Shipped**
- OpenSpec change `expand-n5-curriculum-genki`, the first post-roadmap
  content change (M1–M5 were already complete) — the actual teaching
  content was the weakest part of the product: one course, five lessons,
  ~15 minutes of material behind a fully-built polyglot backend. Deepened
  all five existing lessons (more vocabulary, fuller grammar explanations,
  more example sentences) and added five new lessons continuing the course
  through the next block of Genki I's grammar — existence
  (あります／います), telling time, daily-routine verbs with を／に／で,
  past tense, and い/な-adjectives with すき／きらい. Original writing
  referencing Genki I's topic sequence, not reproduced textbook text.
- New Flyway migration (`V11__expand_n5_lessons.sql`): `UPDATE` for the
  five existing lesson rows (content only — id/position/course_id
  untouched, so `user_lesson_progress` stays valid), `INSERT` for the five
  new ones at positions 6–10. Deliberately chose a migration over the
  `admin-content-authoring` CRUD API for this: Flyway is the only mechanism
  that guarantees every fresh environment (local compose, CI, a rebuilt
  cluster) has the content from first boot — the admin API remains the
  right tool for live, ad-hoc edits after this lands, not baseline seed
  data.
- New lessons need zero exercise-seeding work — `ai-exercise-svc` already
  generates and caches exercises for any lesson on first request; verified
  lesson 6 live and got real, content-specific questions (quizzing
  あります vs います, and した) in ~25s, graded correctly, completion
  propagated to progress (`{"completed":3,"total":10}` after completing
  lessons 1, 4, and 6 with a fresh test user).
- A real constraint caught during design, before any content was written:
  `V9`'s two seeded exercises on lessons 1 and 4 quote specific phrases
  verbatim (ありがとうございます, すみません, and the は／です pattern in
  わたしは がくせい です) — rewriting those lessons had to keep those exact
  terms intact or two already-correct exercises would silently become
  wrong. Verified directly: submitted the original correct answers against
  the rewritten lessons and all four still graded `correct:true`.

**Errors & lessons**
- *A UUID typo caught before running the migration, not after*: the tenth
  lesson's id was briefly written as `...00000000000a` (hex) instead of
  `...000000000010` (decimal), breaking this project's established
  zero-padded-decimal ID convention. Caught by re-reading the file
  immediately after writing it, before applying anything.
- *A local-only bug found along the way, unrelated to this change*:
  gitignored `infra/.env` had `claLLM_KEYS_ENV_PATH` instead of
  `LLM_KEYS_ENV_PATH` (a stray `cla` prefix, cause unknown — possibly a
  paste artifact from an earlier session), silently breaking the LLM keys
  wiring for local `ai-exercise-svc` verification. Fixed directly since the
  file only contains a path, not a secret value.

## 2026-07-27 — demo rehearsal against production found (and fixed) two real bugs

**Shipped**
- Rehearsed `docs/DEMO_SCRIPT.md` live against https://hanatalk.online, step
  by step, rather than trusting that "all pods 1/1 Running" from the earlier
  deploy session meant the actual user-facing flow worked. It didn't, twice
  over — see below. Checked the `exercises` table directly via `psql` first
  (not the API) to see which of the five seeded lessons already had cached
  exercises without accidentally triggering generation on a fresh one;
  rehearsed against lesson 2, left lessons 3 and 5 completely untouched for
  the real interview.
- After both fixes (below), verified the entire script end to end: real
  Gemini-generated MCQ + fill-in-blank exercises rendered for a never-before-
  requested lesson, a correct answer produced the completion banner with no
  page reload, `GET /api/users/me/streak` and `GET /api/leaderboard` both
  returned correct data through the Kafka → `event-worker` path, and the full
  admin flow worked (403 as a regular user → SQL promotion → 201 course
  creation → 204 cleanup delete).

**Errors & lessons — two real bugs, both invisible to every prior health
check**
- *The frontend hadn't been redeployed since M2 (8 days stale).* Opening a
  lesson showed no "Practice exercises" section at all — not even a loading
  state, no console error, nothing. Root cause: `exercise-practice-ui`
  (2026-07-20) shipped a new frontend image, but nobody ever ran
  `kubectl rollout restart deployment/frontend` against production after —
  the deploy step is a separate manual action from the CI that builds the
  image, and it was simply never done. `kubectl get pods` alone can't catch
  this: a pod can be perfectly healthy while running week-old code. Fixed
  with a rollout restart; confirmed via `git log --since -- frontend/` that
  this was the only frontend change stuck behind it.
- *MongoDB had never actually been reachable from any other pod, the entire
  5 days it's been deployed.* Once the frontend fix let the real request
  through, it failed with `pymongo.errors.ServerSelectionTimeoutError:
  mongo:27017: Connection refused`. `infra/k8s/mongo/mongo.yaml`'s
  `command: ["mongod", "--wiredTigerCacheSizeGB=0.25"]` replaces the official
  image's `ENTRYPOINT` (`docker-entrypoint.sh`) outright, so its usual
  auto-injection of `--bind_ip_all` never ran — `mongod` silently fell back
  to its own loopback-only default. The readiness/liveness probes
  (`mongosh` exec'd from inside the same pod, effectively `127.0.0.1`)
  had been passing the whole time, which is exactly why this stayed
  invisible: pod health and Service reachability are different things, and
  nothing before this rehearsal had ever actually sent real cross-pod
  traffic to Mongo. Confirmed the diagnosis directly (`socket.create_connection`
  from inside the `ai-exercise-svc` pod, then inspecting `mongod`'s actual
  listening sockets — all bound to `127.0.0.1`) before touching anything,
  rather than guessing. Fixed by adding `--bind_ip_all` explicitly. Local
  docker-compose never hit this because it never overrides `command` for
  Mongo, so the image's own entrypoint script handles it there.
- *A cascade of shell/quoting failures trying to run one `UPDATE` statement*,
  none of them the SQL's fault: (1) a raw `kubectl exec ... psql -c "..."`
  attempted directly by the assistant was correctly blocked by the harness's
  permission classifier as a direct production DB write — by design, not a
  bug; (2) the user's first attempt ran it via `ssh oci` directly on the VM,
  which failed because `/etc/rancher/k3s/k3s.yaml` is root-only on Oracle
  Linux 9 (a previously-documented gotcha, just newly relevant here); (3) a
  copy-pasted long single-line command silently lost its closing quote in
  transit, leaving the shell open on a `>` continuation prompt with nothing
  actually executed; (4) `export KUBECONFIG=...` is invalid in the user's
  actual shell (fish) and failed silently rather than erroring, so `kubectl`
  fell back to its hardcoded `localhost:8080` default; (5) even `env
  KUBECONFIG=... kubectl ...` (normally shell-agnostic) hit the same
  `localhost:8080` fallback, traced to the SSH tunnel having quietly died at
  some point during the long session — restarted it and confirmed
  reachability directly before troubleshooting further; (6) a final quoting
  loss on the same long `-c "..."` line reproduced even with an explicit
  `--kubeconfig=` flag. Resolved by sidestepping shell quoting entirely:
  `kubectl exec -it postgres-0 -- psql ...` into an interactive session and
  typing the `UPDATE` directly at the `psql` prompt, where it never has to
  survive being embedded in a shell command line at all. Lesson: after two
  failed attempts at getting the same string through shell-plus-copy-paste
  intact, stop trying to fix the quoting and remove the shell from the path
  instead.
- *JWT expiry (1 hour, `JwtService.kt`) surfaced mid-rehearsal as a plain
  401*, not a bug — the demo user's token from early in the session had
  simply expired by the time the admin-flow retry happened, after the
  quoting saga above ate real wall-clock time. Fixed by logging back in.
  Added a note to `docs/DEMO_SCRIPT.md`'s prep section: don't register the
  throwaway demo user more than ~45 minutes before the actual interview
  starts.

## 2026-07-27 — ai-exercise-svc, event-worker, and MongoDB deployed to production

**Shipped**
- Closed the gap `grafana-cloud-observability` and `architecture-doc-and-demo-script`
  both explicitly called out: `ai-exercise-svc` and `event-worker` had only
  ever run via `docker-compose`. Deployed both to the k3s cluster, plus
  MongoDB (`infra/k8s/mongo/` — a bare single-replica StatefulSet, no auth,
  same ClusterIP-only network isolation as Kafka).
- Three secrets created/updated directly against the cluster through the SSH
  tunnel — none of their values were ever pasted into chat or read by the
  assistant. `core-api-secret` gained `OTEL_EXPORTER_OTLP_AUTH` via an
  add-only `kubectl patch` (never had to re-supply the existing DB/JWT
  values); `ai-exercise-svc-secret` and `event-worker-secret` created fresh
  from `~/.config/dev-projects/llm-keys.env` and
  `~/.config/dev-projects/grafana-cloud.env`. `core-api-config` gained
  `AI_EXERCISE_SVC_URL`/`EVENT_WORKER_URL`.
- Production now runs the full five-service architecture live — the
  `docs/DEMO_SCRIPT.md` scoping note ("production doesn't have
  `ai-exercise-svc`/`event-worker` deployed") is now stale; re-scoping the
  demo to the live site is a natural next step, not done in this session.

**Errors & lessons — three real bugs found by actually deploying, not just
building**
- *event-worker's Dockerfile shipped amd64 binaries under an arm64-tagged
  manifest.* `ARG TARGETARCH=amd64` had a **hardcoded default at global
  scope** — the documented anti-pattern for BuildKit's auto-populated
  platform args: a literal default shadows BuildKit's per-platform
  injection during a multi-arch push, so every platform silently built with
  the same value. First fix attempt (just re-declaring `ARG TARGETARCH`
  after `FROM`) wasn't enough on its own; the global hardcoded default had
  to go too. Caught by not trusting registry manifest metadata — pulled the
  binary into a debug pod and read the raw ELF header (`e_machine`) to
  confirm the actual architecture directly, twice, before touching prod
  again.
- *event-worker's `DB_URL` was built via raw k8s `$(VAR)` string
  interpolation* (`postgres://$(DB_USERNAME):$(DB_PASSWORD)@postgres:5432/...`)
  — plain-text expansion with zero escaping. The generated `DB_PASSWORD`
  contains a URL-meaningful character, which broke DSN parsing outright once
  actually deployed (this had simply never been exercised against a real
  generated password before). Fixed the way core-api already does it —
  credentials never go in the URL string; `DB_USERNAME`/`DB_PASSWORD` stay
  separate env vars, combined into the DSN via Go's `net/url` (which
  percent-encodes correctly) inside `config.Load()`. Added a regression test
  covering exactly this failure class.
- *Editing a k8s manifest isn't the same as applying it.* Fixed and
  committed the `DB_URL` change above, rebuilt via CI, restarted the
  deployment — and hit the exact same crash, because the **old** Deployment
  spec was still live. The fix reached git (triggering the image build) but
  was never `kubectl apply`'d — there's no GitOps here, applying manifests
  is a manual step entirely separate from the CI that builds images.
  Applied it; then it worked.
- *Grafana Cloud auth 401 across all three services turned out to be a file
  bug, not a code bug.* `~/.config/dev-projects/grafana-cloud.env`'s
  `OTEL_EXPORTER_OTLP_AUTH` line had an unquoted value containing a space
  ("Basic \<token\>") — sourcing it in a shell script silently truncated it
  to just the word "Basic" (5 characters). Diagnosed by checking the string
  length after sourcing (`${#VAR}`) and testing the credential directly with
  `curl` against the real Grafana OTLP endpoint, bypassing every SDK's own
  header formatting to isolate the credential itself from how each language
  encodes it. Fixed by quoting the full value; length went from 5 to 222 and
  every service's export errors stopped immediately.
- Common thread across all three: verify the actual artifact (binary
  architecture, live pod env, credential length/response) instead of
  trusting that a fix which *looks* right in a diff actually took effect.

## 2026-07-21 — M5 final: architecture doc + demo script — roadmap complete

**Shipped**
- OpenSpec change `architecture-doc-and-demo-script`: the last M5 item and
  the last item on the whole roadmap. The first change in this project
  with zero code — pure synthesis of five milestones of decisions already
  made and dated in this file and `docs/ROADMAP.md`'s decision log.
- New `docs/ARCHITECTURE.md`, organized around 8 questions an interviewer
  would actually ask rather than chronologically: system overview (with
  the two independent core-api↔event-worker relationships — Kafka publish
  vs. sync HTTP reads — named explicitly, since they're easy to conflate),
  why polyglot, the sync/async boundary, the outbox trade-off (with the
  real M1 bug that motivated the current fire-and-forget design), ownership
  boundaries under one shared Postgres, the security model, the
  observability approach, and a genuinely specific "what I'd do differently
  at scale" section grounded in this project's own cut lines (a
  transactional outbox, a real per-service database, circuit-breaker-based
  provider failover, a frontend admin UI) — not a hedge.
- New `docs/DEMO_SCRIPT.md`: a literal timed ~2-minute sequence. Explicitly
  asked the user how to scope it first, since a real finding surfaced
  while planning it — production (hanatalk.online) doesn't actually have
  `ai-exercise-svc` or `event-worker` deployed (confirmed back during
  `grafana-cloud-observability`), so the live site can't show LLM
  generation/failover, Kafka-driven streaks, or cross-service tracing at
  all. Scoped to the local docker-compose stack (the user's choice) so
  the full story can actually be demonstrated end to end.
- Fixed real `README.md` staleness surfaced while writing the above: the
  roadmap table had M3/M4/M5 unchecked despite all three being fully
  shipped; a design-decisions bullet still described AI-exercise-service
  failover in future tense ("will use...") despite `provider-failover-chain`
  having shipped a session ago; the API surface table still said "admin
  role planned" despite `admin-content-authoring` having shipped hours
  earlier in this same session.

**Errors & lessons**
- *A real arithmetic error caught on self-review*: an early draft of the
  security section said the admin-role gap existed for "seven weeks of
  milestone history" — the actual dates (M1 2026-07-14 to M5 2026-07-21)
  span about a week, not weeks at all. Caught by re-reading the doc before
  finalizing rather than trusting the first draft's phrasing — the same
  discipline this document itself argues for (verify against the actual
  dated history, don't just narrate confidently).

## 2026-07-21 — M5 step 2: Grafana Cloud observability

**Shipped**
- OpenSpec change `grafana-cloud-observability`. Scoped to core-api only
  after discovering `ai-exercise-svc`/`event-worker` were never actually
  deployed to production (`infra/k8s/` has manifests only for core-api,
  frontend, postgres, kafka, ingress) — surfaced explicitly and confirmed
  with the user rather than assumed away; deploying those two services is
  separate future work.
- core-api now ships metrics (new `io.micrometer:micrometer-registry-otlp`
  dependency) and traces (already working since `cross-service-tracing`)
  directly to Grafana Cloud's OTLP endpoint — no Grafana Alloy, no
  Collector, no in-cluster agent at all, which is the actual "saves
  cluster RAM" point. `management.otlp.metrics.export.enabled` defaults
  `false` so local dev/CI are unaffected unless explicitly configured.
- Verification reused the exact external-secrets-file pattern from the LLM
  keys work: a new `~/.config/dev-projects/grafana-cloud.env`, referenced
  via `GRAFANA_CLOUD_ENV_PATH` in `infra/.env`, let `docker-compose`'s
  core-api point at the user's *real* Grafana Cloud stack for genuine live
  verification — this session never read the file's contents directly.
- Production rollout is documented (`infra/k8s/README.md` §12), not
  automated: the non-secret OTLP endpoint URLs are checked into
  `core-api-config` directly (confirmed working, not placeholders), but
  the Basic-auth header is the user's action against the live cluster,
  same trust boundary as every other secret in this project.
- Added `infra/grafana/core-api-overview.json`, a checked-in dashboard
  (request rate, error rate, JVM memory, Kafka publish rate) — verified
  live with real data for all four panels, including deliberately
  triggering a 502 (by calling `/api/leaderboard` while `event-worker`
  wasn't running in the test session) to confirm the error-rate panel
  actually renders when a real error exists, not just when it's empty.

**Errors & lessons**
- *Same endpoint-path convention bug, new context*: exactly the
  Spring-vs-native-SDK OTLP path mismatch documented during the local
  Jaeger work recurred against Grafana Cloud — Spring's
  `management.otlp.tracing.endpoint` needs the full `/v1/traces` path,
  but Grafana Cloud's setup wizard hands you the bare base URL (matching
  the *generic* OTel SDK convention, which auto-appends the path itself).
  First symptom: silent 404s in the logs. Fixed by appending `/v1/traces`
  to the user's `OTEL_EXPORTER_OTLP_ENDPOINT` value.
- *Grafana Cloud access-policy scopes are per-signal*: after fixing the
  path, both signals failed with 401 — and the metrics error response
  (unlike the trace exporter's generic "Unauthorized") included the exact
  reason: `"authentication error: invalid scope requested"`. The access
  policy backing the API token needed both `metrics:write` and
  `traces:write` explicitly; having one doesn't imply the other.
- *Grafana Cloud's built-in "Test connection" button uses a stricter query
  than what we actually emit*: it checks for `resource.service.name`,
  `resource.service.namespace`, AND `resource.deployment.environment` all
  matching — but core-api only sets `service.name` (from
  `spring.application.name`); we never configured the other two resource
  attributes. The button reporting failure didn't mean data wasn't
  arriving — checking directly in Explore with just
  `{resource.service.name="core-api"}` confirmed traces were present all
  along. Lesson: a vendor's canned "test connection" check can encode
  assumptions about *how* you were expected to instrument (their wizard's
  own convention) that don't hold for a different instrumentation path —
  don't treat it as ground truth over checking the raw data yourself.
- *Metric names required live confirmation, not documentation-based
  guessing*: the dashboard's first draft guessed `http_server_requests_seconds_count`
  and `kafka_producer_record_send_total` (no `topic` label) based on
  general Prometheus/Micrometer conventions. Both were wrong in
  specifics: Micrometer's OTLP registry actually uses
  `http_server_requests_milliseconds_count` (milliseconds, not seconds —
  a real naming difference from Prometheus-scrape format) and
  `kafka_producer_topic_record_send_total` (the per-topic variant, needed
  for the `by (topic)` grouping the panel already used). Found both by
  searching Explore with `{__name__=~".*http.*"}`/`{__name__=~".*kafka.*"}`
  against the live instance rather than guessing further from docs.

## 2026-07-21 — M5 step 3: admin role for content CRUD

**Shipped**
- OpenSpec change `admin-content-authoring`, closing a real, live gap:
  `CourseController`/`LessonController` have had full POST/PUT/DELETE
  since M1, but `SecurityConfig` only ever checked `.authenticated()` on
  them — any registered user could create, edit, or delete any course or
  lesson. Confirmed via a fresh `@WithMockUser`-role test failing before
  the fix (403 expected, got 200/201/204) and passing after.
- `User` gained a `role` column (`UserRole.USER`/`ADMIN`, `V10__add_role_to_users.sql`,
  defaulting existing rows to `USER`). `UserDetailsServiceImpl` now looks
  up the real role (`.roles(user.role.name)`) instead of hardcoding
  `"USER"` for every principal. `JwtAuthFilter` needed **zero** changes —
  it already re-derives `UserDetails` (and therefore authorities) from the
  database on every request via `loadUserByUsername`, so no JWT `role`
  claim was added: it would have been redundant, and would introduce a
  staleness risk (a demoted admin's already-issued token would keep
  asserting the old role until expiry, if anything ever read
  authorization from the claim instead of the live lookup).
- `SecurityConfig` gained three `hasRole("ADMIN")` matchers (POST/PUT/DELETE
  on course and lesson paths), placed before the existing
  `anyRequest().authenticated()` catch-all — every GET matcher (courses,
  lessons, exercises) is untouched.
- **No API path can create or promote an admin.** Self-registration always
  defaults to `USER`; the only way to become an admin is a direct
  `UPDATE users SET role = 'ADMIN' WHERE email = '...'` an operator runs —
  the same trust boundary already used for `core-api-secret` and the
  Cloudflare Origin CA secret (created directly on the cluster, never
  through a checked-in bootstrap script). No admin-invite endpoint, no
  bootstrap seed with a checked-in credential.
- Updated the *existing, already-passing* `CourseControllerTest`/
  `LessonControllerTest` mutation tests to `@WithMockUser(roles =
  ["ADMIN"])` (they'd been silently exercising the pre-fix security gap
  the whole time), and added new 403-for-non-admin tests alongside them.
- Verified live: a fresh user's `role` defaults to `USER` in the database;
  that user gets 403 attempting to create a course; after a manual SQL
  promotion to `ADMIN`, the exact same JWT (still re-checked against the
  database per request, no re-login needed) can create, update, and
  delete both a course and a lesson; all GET endpoints work identically
  for both roles, exactly as before this change.

**Errors & lessons**
- None — the existing `JwtAuthFilter`/`UserDetailsServiceImpl` split
  (re-deriving authorities from the database on every request, rather
  than trusting anything baked into the JWT) turned out to already be
  exactly the right shape for adding a role check with zero authentication
  code changes. Worth remembering next time a "should this go in the JWT
  claim or get looked up fresh" question comes up elsewhere in this
  codebase: fresh lookup already IS the existing pattern here.

## 2026-07-21 — M5 step 1: cross-service tracing actually works now

**Shipped**
- OpenSpec change `cross-service-tracing`: the first M5 slice, split out
  because it needed no external account (unlike Grafana Cloud, deferred).
  Added a local `jaeger` (`all-in-one`) service to `docker-compose.yml` —
  the first time any of core-api's tracing dependencies (present since M1)
  have ever actually exported a span. `management.otlp.tracing.endpoint`'s
  default (`http://localhost:4318/v1/traces`) had been failing silently
  since day one because "localhost" inside a container means the
  container itself.
- **Kafka trace propagation**: one Spring property,
  `spring.kafka.template.observation-enabled: true`, was the entire fix on
  the producer side — verified directly against Spring Kafka's own
  `sample-08` before writing it, rather than guessing. `EventPublisher`
  needed zero code changes.
- **A real bug found via research, not luck**: `AiExerciseSvcClient` and
  `EventWorkerClient` (from M3/M4) both build their `RestClient` via the
  static `RestClient.builder()` factory method. Spring Boot's own docs
  state plainly that this bypasses all auto-configuration — including the
  `ObservationRestClientCustomizer` that injects `traceparent` headers.
  Both were fixed to inject the Spring-managed `RestClient.Builder` bean
  instead, keeping their existing per-service timeout customization.
  Neither client's request-building code needed to change.
- `ai-exercise-svc` gained `opentelemetry-sdk` +
  `opentelemetry-exporter-otlp-proto-http` +
  `opentelemetry-instrumentation-fastapi`; FastAPI auto-instrumentation
  extracts an incoming `traceparent` header with zero per-route code.
- `event-worker` gained the Go OTel SDK + `otlptracehttp` +
  `otelhttp` (wraps its internal API for incoming HTTP trace context) plus
  **manual** header extraction in the Kafka consumer — there's no
  standardized `kafka-go` OTel instrumentation the way there is for HTTP,
  so `extractTraceContext` reads `traceparent`/`tracestate` off
  `kafka.Message.Headers` into a `propagation.MapCarrier` by hand.
- Verified live in Jaeger (`localhost:16686`), not just by trusting
  config: a single trace spans core-api's `POST .../complete` → its Kafka
  publish → `event-worker`'s `consume exercise.completed`; a separate
  trace spans core-api's request into `ai-exercise-svc`'s `/generate`; a
  third spans core-api's request into `event-worker`'s internal
  leaderboard/streak API.

**Errors & lessons**
- *Jaeger's exact tag mattered*: `jaegertracing/all-in-one:1.65` doesn't
  exist as a Docker Hub tag — only `1.65.0` (or `latest`) do. Checked the
  Docker Hub tags API before guessing a second time.
- *OTLP endpoint env var convention differs by SDK*: Spring's
  `management.otlp.tracing.endpoint` wants the **full URL including
  `/v1/traces`**; the native Python and Go OTel SDKs read the **base**
  `OTEL_EXPORTER_OTLP_ENDPOINT` (host:port only) and append `/v1/traces`
  themselves. Verified both behaviors directly in each SDK's source
  before wiring `docker-compose.yml`, rather than assuming one convention
  applied everywhere — core-api's env var includes the path, `ai-exercise-svc`'s
  and `event-worker`'s don't.
- *A confusing false alarm, caused by my own test method, not the code*:
  live-testing the Kafka trace path, a lesson completion's streak update
  didn't show up for several minutes and `event-worker`'s logs showed
  nothing past "starting consumer" — looked exactly like a real bug. Root
  cause: I ran `event-worker` locally via `timeout N go run
  ./cmd/event-worker` twice in a row to get faster iteration than
  rebuilding the Docker image. `go run` wraps the compiled binary as a
  child process, and `timeout`'s SIGTERM doesn't reliably propagate
  through that wrapper before the deadline forces a SIGKILL — so neither
  run's Kafka consumer group membership was ever cleanly left, leaving
  zombie members that only expire via session timeout. Two overlapping
  zombie pairs kept forcing the "event-worker" consumer group through
  repeated rebalances, each resetting before any member could stabilize
  long enough to actually fetch a message. Confirmed via
  `kafka-consumer-groups.sh --describe` showing the group cycling through
  generations every ~25-30s, and via the broker's own logs showing "2
  members" joining and failing on a loop. Once left alone (killed the
  strays, let the group settle, restarted cleanly via Docker only), the
  same code processed correctly and traced correctly on the first try.
  Lesson: `go run` is not a reliable way to test graceful-shutdown/signal
  behavior for a process that owns external session state (a Kafka
  consumer group) — restart via the actual container, or send the signal
  directly to the compiled binary's PID, not to `go run`'s wrapper.

## 2026-07-20 — M4: Go event-worker — streaks, leaderboard

**Shipped**
- OpenSpec change `event-worker`: first Go service in the repo, consuming
  `user.registered`/`exercise.completed` (both live since M2 with zero
  consumers until now). Minimal deps by design: `kafka-go` (pure Go, no
  cgo/librdkafka), `pgx/v5` (plain SQL, no ORM), stdlib `net/http`'s
  Go 1.22+ method+path `ServeMux` (no router dependency), `golang-migrate`
  for schema migrations embedded via `//go:embed`.
- **Idempotency via a natural key, not a dedup ledger**: a
  `daily_activity(user_id, activity_date)` table with a UNIQUE constraint
  absorbs both same-day multiple completions and Kafka-redelivered
  duplicates via `ON CONFLICT DO NOTHING` — verified live by hard-killing
  the container mid-flow (`docker kill`) right after a completion and
  confirming the eventual state was correct with no duplicate rows after
  it rejoined the consumer group.
- **Fan-in concurrency pattern**: one goroutine per topic reader
  (`user.registered`, `exercise.completed`), each fetching independently
  and sending a job to a single DB-writing goroutine over a channel;
  offsets are only committed after that goroutine confirms the write
  succeeded — a reader that gets a processing error does not commit,
  guaranteeing at-least-once redelivery on failure.
- **Own `users` projection, not cross-schema reads**: `event-worker`
  builds a local `(user_id, username)` table from `user.registered`
  rather than querying core-api's `users` table directly, even though
  both live in the same Postgres instance — a deliberate ownership
  boundary, not a technical constraint. Verified the leaderboard's
  username comes from this projection, not core-api's database.
- core-api: `UserProfileController` gained `GET /me/streak`; new
  `LeaderboardController` for `GET /api/leaderboard`; both delegate to a
  new `EventWorkerClient` (same `RestClient`-with-timeout shape as
  `AiExerciseSvcClient` from M3). Both endpoints are authenticated by
  default via `SecurityConfig`'s existing catch-all — zero
  `SecurityConfig` changes needed, same pattern as M3's exercise
  endpoints.
- Infra: new `event-worker` compose service, a Dockerfile that
  cross-compiles natively via `GOOS`/`GOARCH` (no QEMU-under-build
  workaround needed, unlike core-api's Gradle build), and
  `.github/workflows/event-worker.yml` (`go vet`, `go test`, multi-arch
  build + push to GHCR).
- Verified live end-to-end: registered a user, completed three lessons
  across two container lifetimes (including one hard-kill), confirmed
  `GET /api/users/me/streak` correctly showed a streak of 1 (same UTC
  day, so all three completions collapsed into a single activity row,
  exactly as designed), confirmed the same for `GET /api/leaderboard`
  with the correct username, and confirmed a brand-new user with zero
  completions gets a 0 streak, not an error.

**Errors & lessons**
- *golang-migrate's postgres driver doesn't create the target schema*:
  passing `SchemaName: "event_worker"` to `postgres.WithInstance` assumes
  the schema already exists — it failed with `schema "event_worker" does
  not exist` on first run, because the driver only creates its own
  `schema_migrations` tracking table, not the schema itself. Fixed by
  running `CREATE SCHEMA IF NOT EXISTS event_worker` over the raw
  connection before handing it to the migrate driver.
- *A silent 20-30s gap after every consumer restart, initially
  mistaken for a bug*: the first live verification attempt showed no
  streak update at all after a completion, with zero relevant log output
  — looked like the consumer wasn't running. It was: Kafka's consumer
  group rebalance protocol takes a session-timeout window before a new
  group member (a freshly restarted container) gets partitions assigned
  and starts actually fetching, even though the reader had already logged
  "starting consumer." Adding a temporary debug log line
  (`fetched message`, later downgraded to `slog.Debug`) made this
  directly observable rather than assumed, and confirmed both queued
  messages were correctly picked up ~20s later. Lesson: a consumer
  process being "up" and a consumer group having "usable partition
  assignment" are different moments — don't declare a Kafka consumer
  broken from a quiet log within the first ~30s of its life.

## 2026-07-20 — M3 step 4: exercise practice UI — M3 fully done

**Shipped**
- OpenSpec change `exercise-practice-ui`: `LessonPage` gains a "Practice
  exercises" section, wired to the `GET /api/lessons/{id}/exercises` and
  `POST /api/exercises/{id}/attempts` endpoints that have been live since
  M3 steps 1–3 with no frontend to use them until now. New
  `ExercisePractice` component (`frontend/src/components/`): MCQ renders
  as a radio-button group, fill-in-blank as a text input, each exercise is
  its own independent mini state machine (`answer`/`submitting`/
  `result: boolean | null`) — no cross-exercise coordination, matching the
  backend's model where every attempt is independent.
- A correct attempt reuses the *exact same* completion state `LessonPage`'s
  manual "Mark as complete" button already drives — extracted
  `markComplete()`'s success path into a shared `refreshCompletion()`
  function, passed to `ExercisePractice` as an `onCompleted` callback. One
  completion banner, two ways to trigger it, zero duplicated UI.
- Loading state starts as plain "Loading exercises…" and upgrades to
  "Still generating — this can take up to a minute the first time." after
  a 4s `setTimeout`, honest about the ~90s worst-case first-generation
  latency (per `provider-failover-chain`) without alarming users on the
  common fast path (cached/persisted exercises load in well under 4s).
- A failed fetch (network error or a 502 from an exhausted provider chain)
  shows an error message with a manual retry button.
- Verified live end-to-end via Chrome browser automation (no Playwright
  MCP available this session; no automated frontend test runner exists in
  this repo at all — consistent with the rest of the frontend):
  - Opened a lesson with zero exercises: loading message showed, exercises
    rendered once generation completed.
  - Submitted an incorrect MCQ answer → incorrect feedback, immediately
    resubmitted the correct answer → correct feedback **and** the lesson's
    completion banner appeared with no page reload.
  - Reloaded the same lesson: exercises loaded instantly (already
    persisted `Exercise` rows), completion banner still showed.
  - Manual "Mark as complete" on a *different* lesson (one with existing
    exercises, not yet completed) still worked unchanged.
  - Stopped `ai-exercise-svc`, opened a lesson with no exercises yet:
    error + retry UI appeared (no lingering "Loading…"). Restarted the
    service, clicked retry: request succeeded, and — since this happened
    to be that lesson's genuine first-ever generation — the "still
    generating" slow-loading message was also observed live, not just in
    code review.
  - Browser console clean (no errors) throughout.
- **M3 (AI exercises) is now fully done** — all four steps (exercise
  domain, generation via `ai-exercise-svc`, provider failover, frontend
  UI) shipped and verified live.

**Errors & lessons**
- *A stale date, caught before it spread:* the previous session's archive
  of `provider-failover-chain` was named `2026-07-21-...` from an assumed
  date rollover that hadn't actually happened — `date +%Y-%m-%d` on the
  machine still read 2026-07-20. Caught while writing this session's
  ROADMAP entry (about to write "done 2026-07-21" and noticed the
  mismatch); fixed by renaming the archive folder back to
  `2026-07-20-provider-failover-chain` via `git mv` before it was pushed
  anywhere. Lesson: when a date matters for a filename/log entry across a
  session that's run long, check `date` rather than inferring — assistant
  "today" context can go stale mid-session in a way the actual system
  clock won't.

## 2026-07-20 — M3 step 3: provider failover chain (Gemini → Groq → OpenRouter)

**Shipped**
- OpenSpec change `provider-failover-chain`: `ai-exercise-svc`'s
  `app/generation.py` now tries providers in a fixed order — Gemini, then
  Groq, then OpenRouter — falling through to the next on *any* failure
  (transport error, timeout, or a response that fails
  `GenerationResult`'s Pydantic validation). New `app/providers.py` holds
  one function per provider (`call_gemini`/`call_groq`/`call_openrouter`),
  all returning raw JSON text so the same validation call applies to all
  three uniformly.
- Groq and OpenRouter both speak an OpenAI-compatible chat completions API,
  so one `_call_openai_compatible` helper (via the `openai` SDK's
  `base_url` override) covers both — only Gemini needed its own code path.
  Model IDs were looked up against each provider's live docs rather than
  guessed: **Groq** uses `openai/gpt-oss-20b` (currently the only model
  with Groq's *strict* JSON-schema mode — `strict: true` in
  `response_format`, a real schema guarantee, not just "valid JSON").
  **OpenRouter** uses `google/gemma-4-26b-a4b-it:free` — free tier,
  structured-outputs-capable, and deliberately a different model family
  from Groq's gpt-oss so a systemic issue with one weight family doesn't
  take down both fallbacks at once.
- Simulated-failure unit tests (`tests/test_generation.py`, all providers
  mocked, no real API calls): Gemini succeeds → Groq/OpenRouter never
  called; Gemini fails → Groq called, succeeds, OpenRouter never called;
  Gemini and Groq both fail → OpenRouter succeeds; all three fail →
  `GenerationFailedError` (existing 502 mapping unchanged); a
  schema-valid-JSON-but-invalid-shape response (MCQ with empty options) is
  treated exactly like a transport failure and falls through.
- Verified live: forced a real Gemini failure (invalid model name),
  confirmed Groq produced valid, persisted exercises through the real
  fallback path — not just the mocked tests.
- core-api needed **no changes** — `AiExerciseSvcClient`/`ExerciseService`
  only see `ai-exercise-svc`'s `/generate` contract, unaware anything
  changed behind it. One config change: `ai-exercise-svc.timeout-seconds`
  raised 45s → 90s (see below).

**Errors & lessons**
- *Timeout needed a second measurement:* the forced Gemini failure (bad
  model name) took **~60 seconds** to actually fail before falling through
  to Groq — apparently retried internally before giving up — pushing the
  full round trip to ~62s, well past the 45s timeout set in the previous
  session (`ai-exercise-svc` change) from a single successful-call
  measurement. A timeout tuned from the happy path doesn't cover a failure
  path with different latency characteristics; raised to 90s to leave real
  headroom for a Gemini-fails-then-Groq-succeeds round trip, and possibly
  a double fallback to OpenRouter.
- *Key exposure during verification, self-caught, not user-caught:* while
  checking that the container's environment had the right variables set, a
  `sed 's/=.*KEY.*/=<redacted>/'` command was meant to redact secret
  values before printing — but the pattern only matched if the *value*
  contained the literal text "KEY"; only the *variable name* does. Both
  `GROQ_API_KEY` and `OPENROUTER_API_KEY` printed in full into a
  background-task output file, which was then read into the session
  before the mistake was noticed. Both keys were rotated immediately;
  `GEMINI_API_KEY` was never touched this way and needed no rotation.
  Lesson: redacting env var *values* by pattern-matching on the *name*
  doesn't work — either don't print potentially-secret env output at all,
  or filter by variable name (`cut -d= -f1`) instead of trying to mask the
  value.

## 2026-07-20 — M3 step 2: ai-exercise-svc (real LLM-generated exercises)

**Shipped**
- OpenSpec change `ai-exercise-svc`: a new Python/FastAPI service
  (`ai-exercise-svc/`) that generates lesson exercises via Gemini
  (`google-genai`'s Interactions API, structured-output mode with a Pydantic
  schema — the SDK validates the model's JSON against the schema itself,
  raising `ValidationError`/`JSONDecodeError` on a bad response) and caches
  the result in MongoDB keyed by lesson id. Single provider only for this
  slice, by design — Groq and OpenRouter keys exist but are reserved for the
  step-3 failover change so this slice's plumbing wasn't designed under
  multi-provider pressure.
- core-api's `ExerciseService.listByLesson` now calls out to
  `ai-exercise-svc` (new `AiExerciseSvcClient`, a `RestClient` with an
  explicit read/connect timeout — this call is on the user-facing critical
  path, not best-effort like Kafka) whenever a lesson has zero `Exercise`
  rows, persists what comes back as ordinary `Exercise` entities, and serves
  it identically to Flyway-seeded content. A downstream failure surfaces as
  a clean 502, not a silently empty list.
- Infra: `mongo` + `ai-exercise-svc` services added to
  `infra/docker-compose.yml`; new `ai-exercise-svc/Dockerfile` and
  `.github/workflows/ai-exercise-svc.yml` (ruff + pytest + multi-arch GHCR
  push, mirroring `core-api.yml`/`frontend.yml`).
- Verified live against the real Gemini API (not mocked): a lesson with no
  exercises triggered a real generation call, the response was schema-valid
  and persisted, grading/`exercise.completed` (`source=EXERCISE`)/progress
  all worked identically to seeded exercises, a repeat request for the same
  lesson was served from `ai-exercise-svc`'s Mongo cache in milliseconds,
  and the two already-seeded lessons made zero calls to `ai-exercise-svc`.

**Errors & lessons**
- *Timeout tuned from theory, not measurement:* the design's placeholder
  15s client-side timeout turned out to be right at the edge of a real
  first-generation Gemini call's actual latency (~15s), so the very first
  live verification attempt hit the timeout and returned a 502 — the
  request to `ai-exercise-svc` had actually succeeded server-side (FastAPI
  kept running the synchronous handler to completion, including the Mongo
  cache write, even after the client gave up), so the *second* attempt was
  served instantly from cache, momentarily masking the root cause. Fixed by
  measuring the real call directly (`curl` straight to `ai-exercise-svc`,
  bypassing core-api's timeout) and raising the timeout to 45s. Lesson:
  don't guess a synchronous cross-service timeout from a comment — measure
  the real call once before shipping the number.
- *Secrets stayed clean throughout:* the LLM keys file
  (`~/.config/dev-projects/llm-keys.env`) was never read by any tool in this
  session — `docker-compose.yml` references it only via
  `${LLM_KEYS_ENV_PATH}`, resolved from a gitignored `infra/.env`, so the
  tracked compose file never hardcodes a machine-specific path either.

## 2026-07-20 — M3 kickoff: exercise domain in core-api (no LLM yet)

**Shipped**
- OpenSpec change `add-exercise-domain`: the first M3 slice, scoped to need
  zero LLM keys while the user procures them. New `exercise` domain package
  (`Exercise`, `ExerciseAttempt`, repositories), `ExerciseService` (exact-match
  grading — verbatim for MCQ, trim+lowercase for fill-in-blank),
  `ExerciseController` (`GET /api/lessons/{lessonId}/exercises`,
  `POST /api/exercises/{exerciseId}/attempts`), Flyway `V8` (tables) + `V9`
  (4 placeholder exercises across 2 N5 lessons).
- Reused the existing completion mechanism rather than building a second one:
  a correct attempt calls the same `ProgressService.markComplete(...)` the
  manual "Mark as complete" button uses, with a new
  `CompletionSource.EXERCISE`. One `user_lesson_progress` table, one
  `exercise.completed` Kafka event shape, no divergent completion state.
- `correctAnswer` never serializes to the client — enforced by a separate
  `ExerciseResponse` DTO, not `@JsonIgnore` on the entity (matches the
  existing DTO-vs-entity separation in `LessonDtos`/`CourseDtos`).
- Tests: `ExerciseServiceTest` (plain Mockito unit test, first of its kind in
  this codebase — all prior tests were `@WebMvcTest` controller tests;
  needed to directly verify grading logic and the `markComplete` delegation
  args, which a controller test with a mocked service can't reach) +
  `ExerciseControllerTest` (`@WebMvcTest`, matches `ProgressControllerTest`'s
  pattern). `ktlintCheck test bootJar` all green.
- End-to-end verified via `docker compose up -d --build`: registered a fresh
  user, listed lesson 1's exercises (options shown, no answer field),
  submitted one incorrect + one correct MCQ + one correct fill-in-blank
  (different case/whitespace, still graded correct), confirmed
  `GET /api/courses/{id}/progress` shows the lesson complete, and the Kafka
  consumer shows the `exercise.completed` event with `"source":"EXERCISE"`.
  Migration ran cleanly against an existing V7 database (not just a fresh one).
- Explicitly deferred to a follow-up change: `ai-exercise-svc`, provider
  failover, MongoDB, frontend exercise UI — all blocked on LLM key
  procurement (Groq/Gemini/OpenRouter), which the user has in progress.

**Errors & lessons**
- None — went from proposal to green build to verified live flow without a
  wrong turn, largely because the design phase read the actual
  `ProgressService`/`EventPublisher`/entity code first instead of guessing
  at conventions.

## 2026-07-19 — M2 completed: live at https://hanatalk.online

**Shipped**
- Oracle A1 Flex VM finally provisioned (4 OCPU/24 GB aarch64, sa-saopaulo-1)
  after ~4 days of "Out of capacity" retries — with **Oracle Linux 9**, not the
  planned Ubuntu. Kept OL9 rather than risk the capacity slot on a recreate;
  adaptations: `opc` user, `dnf`, firewalld disabled (k3s docs recommend it on
  RHEL-family; the OCI VCN security list is the single firewall, 22/80/443),
  SELinux stays enforcing (`k3s-selinux` auto-installed).
- Single-node k3s v1.36.2 up; kubectl from laptop via SSH tunnel (6443 never
  public). Bring-up per runbook: namespace → secrets (openssl-generated +
  Origin CA TLS) → postgres/kafka StatefulSets → core-api (7 Flyway migrations
  incl. seed) → frontend + ingress. First pod-ready ~30 s each on arm64.
- Kafka restart check passed: pod delete → clean rejoin with pinned CLUSTER_ID.
- GHCR packages made public after discussing public-vs-private trade-off
  (images hold only compiled artifacts; repo is public; avoids PAT-based
  imagePullSecret rotation). Anonymous manifests verified: amd64+arm64.
- Full E2E on the live site via Chrome: register → N5 course → lesson →
  mark complete → checkmark + 20% progress bar; deep links OK; console clean.
  `user.registered` + `exercise.completed` consumed in-cluster from the
  public flow. Best-effort intact: with kafka scaled to 0, login + completion
  still 2xx (~3 s `max.block.ms` penalty).
- Deploy loop rehearsed end-to-end: meta-description commit → CI (52 s) →
  `rollout restart` → live; rollback via **sha-pin escape hatch**
  (`kubectl set image ...:<sha>`) → verified reverted → roll-forward. Plain
  `rollout undo` is a no-op with `:latest` + `Always` — as documented.

**Ops hardening (same day, after go-live)**
- User reshaped the VM 4/24 → 2/12 → back to 4/24 via OCI CLI (confirmed
  entitled to 4/24); IP survived, cluster **self-recovered from both reboots**
  unattended. VM creation trick on record: A2 instance on trial credits →
  CLI reshape to A1 (direct A1 creation kept hitting "Out of capacity");
  trial credits expire ~2026-08-15, after which the trick bills.
- Backups: custom `hanatalk-weekly` boot-volume policy (weekly incremental,
  4-week retention, fits free tier's 5 slots) + manual full baseline taken.
- `dnf-automatic` enabled on the VM (security-only, auto-apply, daily timer).
- Docker CI actions bumped to Node 24 majors (qemu/buildx/login v4,
  build-push v7) — both workflows green.
- SSH alias renamed by user: `hanatalk-vm` → `oci` (CLAUDE.md updated).

**Errors & lessons**
- *GoDaddy ghost records:* hanatalk.online initially served a GoDaddy Website
  Builder 404 through Cloudflare — the zone import had copied old
  builder records; the VM A record wasn't the one being served. Lesson: after
  moving a domain to Cloudflare, audit **all** imported DNS records, not just
  add your own.
- *VCN blocks by default:* 80/443 timed out publicly while everything worked
  in-cluster (verified via `curl -H "Host: ..." https://localhost/` on the VM
  — a useful bisect: proves manifests/TLS/ingress before touching cloud
  firewalls). Ingress rules for 80/443 had to be added in the console.
- *Oracle free-tier rug-pull:* Always Free A1 quietly halved to 2 OCPU/12 GB
  on 2026-06-15, and our 4/24 VM was created **after** the cut — whether it
  stays free is unclear (reports contradict). User watching Cost Analysis with
  a budget alert; the stack fits in 2/12 if a downsize is ever forced.
- *OL9 sudo secure_path* omits `/usr/local/bin` — `sudo k3s` fails; use
  `sudo /usr/local/bin/k3s`.
- *`kubectl wait --for=delete` + StatefulSet recreate race:* after
  `delete pod kafka-0`, waiting for ready can hit the NotFound window while
  the controller recreates it; poll for existence first.

## 2026-07-14 — M1 completed; SDD evaluation; OpenSpec adopted

**Shipped**
- OpenSpec initialized (`openspec init --tools claude`): `openspec/config.yaml`
  with project context + proposal rules, `/opsx:*` slash commands and skills
  under `.claude/`. `.claude/settings.local.json` (per-machine permission
  grants) gitignored. Note: `npm install -g` fails with EACCES on this machine
  (root-owned global prefix) — run the CLI via `npx -y @fission-ai/openspec@latest`.
  This OpenSpec version has no `/opsx:onboard`; baseline specs will accrete as
  changes are archived from M2 onward.
- `completedLessonIds` added to `GET /api/courses/{id}/progress`; course page
  shows per-lesson checkmarks; lesson page pre-marks already-completed lessons.
- Frontend CI workflow (`.github/workflows/frontend.yml`): npm ci → oxlint → build.
- User verified the full browser flow manually; Playwright MCP installed for
  automated browser verification in future sessions.

**Errors & lessons**
- *Stale container shadowing:* verification hit port 8080 and got the **old**
  API — the compose `core-api` container from a previous session was still
  running; the freshly started jar had silently failed with "Port 8080 was
  already in use" while curl kept getting answers (old shape, right counts —
  easy to misread as a code bug). Lesson: `docker ps` before any local-jar
  verification; now in CLAUDE.md gotchas.
- Machine had no git identity configured; set repo-local user.name/email
  matching prior commit authorship.

**SDD tooling evaluation (for M1.5 proposal)**
- Candidates: GitHub Spec-Kit, OpenSpec, others (Kiro, GSD).
- **Recommendation: OpenSpec** — brownfield-first (delta specs against existing
  behavior, exactly our situation), lightweight (propose → apply → archive; no
  constitution/branch-per-feature ceremony), no API keys or MCP dependency,
  most actively maintained SDD framework (~52k stars, mid-2026), native Claude
  Code slash commands (`/opsx:propose`, `/opsx:apply`, `/opsx:archive`).
- Spec-Kit rejected for now: greenfield-oriented, heavier process designed for
  teams; would tax a solo part-time dev without improving the interview story.
- Folder impact is small (a root `openspec/` dir with `changes/` + archived
  specs) — **not** the big restructure originally feared.

## 2026-07-13 — Planning + M1 execution

**Shipped**
- Milestone roadmap approved (see ROADMAP.md). READMEs rewritten to match the
  Japanese-only/JLPT reality (they still described the multilingual pivot-era
  plan). V7 seed migration (N5 course, 5 lessons, fixed UUIDs). CORS
  (env-configurable). React/Vite/TS frontend: auth flow, course browsing with
  JLPT filter, lesson view, mark-complete, progress bar. CI pushes core-api
  image to GHCR on main.

**Errors & lessons**
- *Kafka outage broke registration:* `KafkaTemplate.send()` throws
  **synchronously** when the broker is unreachable (metadata timeout) — the
  `whenComplete` callback only sees async failures. Fixed: try/catch in
  `EventPublisher.send` + `max.block.ms=3000` (default 60s stalled requests for
  a minute). Design intent restored: events are best-effort side effects.
- *5xx masquerading as 401:* Spring Security intercepted the `/error` dispatch
  (not permitAll), so every server error reached clients as an empty 401 —
  which hid the Kafka bug above. `/error` is now permitted.
- *`bitnami/kafka` image gone:* Bitnami withdrew free Docker Hub images (2025);
  `docker compose up` failed for fresh clones. Switched to `apache/kafka:3.9.1`
  with dual listeners (containers `kafka:29092`, host `localhost:9092`).
- *Machine setup:* no Node.js existed (frontend built via a scratchpad Node 24
  tarball; user has since installed system Node). `gradlew` is not executable —
  use `sh gradlew`.

**Known API gap fixed next session:** progress endpoint had no per-lesson data.
