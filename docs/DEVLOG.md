# DEVLOG

Newest first. Every working session gets an entry: what shipped, what broke,
and root causes — so no lesson has to be relearned.

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
