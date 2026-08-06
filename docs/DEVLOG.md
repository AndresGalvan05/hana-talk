# DEVLOG

Newest first. Every working session gets an entry: what shipped, what broke,
and root causes — so no lesson has to be relearned.

## 2026-08-06 — Grafana dashboards for ai-exercise-svc and event-worker

**Shipped**
- Two new checked-in dashboard JSON files,
  `infra/grafana/ai-exercise-svc-overview.json` and
  `infra/grafana/event-worker-overview.json`, following the exact same
  pattern as the existing `core-api-overview.json` — a manually-imported
  artifact (paste into Grafana Cloud's "Import dashboard" UI, select the
  Prometheus datasource for the templated `DS_PROMETHEUS` input), not
  API-provisioned, matching that dashboard's original design rationale
  (no Grafana API token with write access needed; a durable, reviewable
  artifact in the repo).
- Direct implementation, not a dedicated OpenSpec change — matching the
  precedent that the original `core-api-overview.json` was itself just
  one task inside a broader observability change, not a standalone
  slice, and this is comparably scoped (two static JSON files, no
  application code, no tests).
- Every one of the 8 panels' PromQL queries was run directly against
  real Grafana Cloud data via Explore before being written into the
  dashboard JSON — not assumed from memory of the metric names chosen
  during implementation. This caught a real, non-obvious fact: the two
  services use **different OTel HTTP semantic-convention versions**.
  `ai-exercise-svc`'s `FastAPIInstrumentor` emits the older convention
  (`http_server_duration_milliseconds_{bucket,count,sum}`, labeled
  `http_target`/`http_method`/`http_status_code`), while
  `event-worker`'s `otelhttp` (a newer contrib module version) emits the
  stable convention (`http_server_request_duration_seconds_{bucket,
  count,sum}`, labeled `http_route`/`http_request_method`/
  `http_response_status_code`). The two dashboards' HTTP panels are
  deliberately *not* copy-pasted from each other — each uses its
  service's real label set, with a note panel on each dashboard
  explicitly documenting the difference so it doesn't get "fixed" into
  a false consistency later.

**Errors & lessons**
- None this time — every panel query was verified against live data
  before being committed, continuing the discipline established
  earlier the same day when the metrics themselves were first checked.

## 2026-08-06 — `service-metrics-export` follow-up: event-worker's metrics never actually reached Grafana Cloud

**What happened**
- Asked to check Grafana Cloud for the new metrics after archiving
  `service-metrics-export`. `ai-exercise-svc`'s `llm_call_duration_seconds`/
  `llm_call_total` and standard `http_server_*` metrics were live and
  correctly labeled. **`event-worker` had zero metrics of any kind** —
  not just the new Kafka ones, nothing at all, confirmed via
  `{service_name="event-worker"}` returning no data in Explore.

**Root cause**
- `infra/k8s/event-worker/deployment.yaml` wires every env var
  individually (`env: - name: X, valueFrom: configMapKeyRef: ...`) —
  unlike `infra/k8s/ai-exercise-svc/deployment.yaml`, which uses a
  blanket `envFrom: - configMapRef:`. Adding `OTEL_METRICS_ENABLED` to
  `event-worker-config`'s data (task 5.2, done during the original
  implementation) updated the ConfigMap but never reached the pod's
  actual environment, since nothing referenced that key explicitly.
  Confirmed directly: `kubectl exec deployment/event-worker -- env`
  showed `OTEL_EXPORTER_OTLP_ENDPOINT`/`_HEADERS` but no
  `OTEL_METRICS_ENABLED` at all, even after the original rollout.
  `ai-exercise-svc`'s identical-looking task (5.1) worked purely because
  its deployment happens to use the blanket-envFrom pattern — the two
  services' env-wiring conventions were never actually the same, and
  nothing in the original implementation checked that.

**Fix**
- Added the missing explicit `env:` entry
  (`valueFrom: configMapKeyRef: name: event-worker-config, key:
  OTEL_METRICS_ENABLED`) to `deployment.yaml`, applied it, restarted the
  deployment. Confirmed the fix two ways: `kubectl exec ... -- env` now
  shows `OTEL_METRICS_ENABLED=true`, and — the real test — a fresh
  production lesson completion produced `kafka_message_processing_duration_seconds`
  data in Grafana Cloud Explore within the next export cycle, correctly
  labeled by `topic`.

**Lesson**
- "The ConfigMap has the key" and "the pod's environment has the key"
  are not the same fact in this repo, because the two services picked
  different env-wiring conventions for reasons unrelated to metrics
  (event-worker's per-var wiring predates this change, likely to keep
  secret-sourced DB credentials next to their non-secret counterparts
  explicitly). A config change task should say which wiring convention a
  service actually uses, not assume "add the key to the configmap" is a
  complete description of the change — and verification should confirm
  the value inside the running pod's environment, not just the
  ConfigMap object, exactly as this check ended up doing by accident
  when asked to look at Grafana Cloud rather than trusting the earlier
  "rolled out successfully" pod-log check (which only confirms the
  process started, not that every env var it depends on is actually
  present).

## 2026-08-06 — `service-metrics-export`: metrics for ai-exercise-svc and event-worker

**Shipped**
- OpenSpec change `service-metrics-export` — the last item from the
  post-roadmap planning session's original list ("extended
  observability"), but research done before writing the proposal
  corrected the assumed scope: tracing for `ai-exercise-svc` and
  `event-worker` was already live in Grafana Cloud, shipped by
  `cross-service-tracing` earlier this session. The real remaining gap
  was narrower — only core-api exported any metrics. Confirmed this
  precisely (not assumed) by reading both services' actual OTel setup
  code before scoping the change.
- `ai-exercise-svc` gets a `MeterProvider` alongside its existing
  `TracerProvider`, plus a duration histogram + success/failure counter
  recorded inside `call_with_fallback` for every LLM provider attempt
  (labeled by provider). `event-worker` gets a parallel
  `internal/metrics` package mirroring `internal/tracing`'s shape, plus
  a duration histogram + success/failure counter recorded in the
  consumer's existing per-message tracing-span site (labeled by topic).
  Both services' standard HTTP request metrics come for free once a
  `MeterProvider` exists, since `FastAPIInstrumentor` and `otelhttp`
  already wrap the request paths for tracing.
- Export is gated behind a new `OTEL_METRICS_ENABLED` flag (default
  off), mirroring core-api's existing `GRAFANA_CLOUD_METRICS_ENABLED`
  pattern and its stated reason: local Jaeger only accepts OTLP traces,
  not metrics, so an always-on exporter would error against it locally.

**Errors & lessons**
- *Found and fixed a real circular-import bug during implementation*,
  not just a test artifact: the design doc's plan to have
  `app/llm_fallback.py` import a shared `meter` from `app/main.py`
  would have created `main -> routes -> generation/chat -> llm_fallback
  -> main`. Fixed by having `llm_fallback.py` call
  `opentelemetry.metrics.get_meter(...)` directly against OTel's global
  registry instead — safe regardless of import order, because OTel's
  Python (and Go) APIs both implement a documented proxy-provider
  mechanism: instruments created before the real provider is registered
  get upgraded automatically once it is.
- *Changed the design's provider-naming approach after implementation
  revealed a subtler bug it would have caused*: a `{call_gemini:
  "gemini", ...}` dict keyed by function identity, built once at import
  time, would silently stop matching once a test monkeypatches
  `app.llm_fallback.call_gemini` to a mock — the very thing an existing
  code comment in that file explains the provider tuple is deliberately
  re-resolved from module globals on every call to support. Fixed with
  `(name, callable)` tuples instead, which don't depend on function
  identity at all.
- *Found a real Go OTel SDK constraint while writing `event-worker`'s
  metrics test*: the global `MeterProvider`'s proxy-upgrade only
  happens the *first* time `otel.SetMeterProvider` is called
  process-wide — a second call in a separate, later test doesn't
  retarget already-created package-level instruments to the new test's
  reader. An initial two-separate-tests design silently lost data in
  the second test as a result; fixed by combining both assertions into
  one test sharing a single provider/reader.
- Verified the "metrics export is non-fatal when it can't reach a valid
  OTLP metrics receiver" behavior directly, not just assumed: ran both
  services locally with `OTEL_METRICS_ENABLED=true` pointed at local
  Jaeger (which only accepts traces), waited past the default ~60s
  export interval, confirmed both stayed up with no crash and no
  unhandled exception — matching this app's established
  best-effort-observability principle (same one already documented for
  Kafka publishing).
- Along the way, hit and cleared a stale/pre-existing MongoDB
  `generated_exercises` cache entry (from an earlier session, unrelated
  to this change) that was failing Pydantic validation on read — the
  same "stale cache" class of issue already documented from this
  session's very first production-incident fix.
- Could not personally verify the Grafana Cloud Explore/metrics-browser
  side of the export — those credentials live in a gitignored env file
  outside the repo, deliberately not read directly. Real production
  activity was triggered (a chat message, a lesson completion) to
  exercise both instrumented code paths; confirming the data actually
  landed in Grafana Cloud is a follow-up check for the user.

## 2026-08-02 — `responsive-mobile-ui`: mobile nav + layout fixes

**Shipped**
- OpenSpec change `responsive-mobile-ui` — the first frontend slice
  driven directly by user feedback ("focus more on frontend, menus, and
  mobile compatibility") rather than a pre-planned post-roadmap item.
  Confirmed before writing any code: `index.css` had zero `@media`
  queries anywhere across its ~755 lines, and the header nav had grown
  to 7 items across every slice shipped this session with no wrap — a
  real, unaddressed gap, not a hypothetical one.
- A hamburger + slide-out drawer for the nav below a single `640px`
  breakpoint (`Layout.tsx` + `index.css`), closing automatically on
  navigation (`useLocation().pathname` in a `useEffect`) or backdrop
  click. Chosen over a bottom tab bar or "just let it wrap" after asking
  the user directly.
- Mobile-safe layout for `.lesson-prev-next` (stacks to one column),
  `.admin-list-row`/`.leaderboard-row` (wrap instead of overflowing),
  and the vocabulary table (new `.table-scroll` horizontal-scroll
  wrapper). Plus a small visual-polish item requested alongside the
  mobile work: achievement cards get a ✅/🔒 icon prefix so locked/
  unlocked status isn't opacity-only.

**Errors & lessons**
- *`resize_window`, the browser-automation tool's only viewport-control
  primitive, silently did not resize the actual OS window in this
  environment* (likely the Linux tiling window manager overriding
  programmatic resize requests) — confirmed via `window.innerWidth`
  staying at the original size after multiple resize calls that each
  reported success. Worked around it with a same-origin `<iframe>`
  fixed at a target CSS pixel width (375/768/1000px), injected via
  `javascript_tool` — this correctly triggers real `@media` query
  evaluation (confirmed via `matchMedia(...).matches` inside the
  iframe), unlike a scaled screenshot would, and is same-origin so it
  shares `localStorage`/auth state with the parent tab for free.
- *Found and fixed a real CSS bug during verification, not just a test
  artifact*: the `.lesson-prev-next { grid-template-columns: 1fr; }`
  mobile override alone didn't stack the layout — computed style still
  showed two columns. Root cause: an unrelated existing rule,
  `.lesson-nav-next { grid-column: 2; }`, forced CSS Grid to
  auto-generate an implicit second column to satisfy that explicit
  placement, even with the container's explicit template collapsed to
  one track. Fixed by also resetting `.lesson-nav-next { grid-column:
  auto; }` inside the same mobile block. A good reminder that a media
  query overriding one property on a container doesn't automatically
  neutralize a *child's* explicit grid placement.
- Verified the fix identically in both the local stack and production
  after deploy (both reporting a single 328px grid column), not just
  locally.

## 2026-08-01 — `admin-content-ui`: a real UI for course/lesson authoring

**Shipped**
- OpenSpec change `admin-content-ui` — third and last of the "close a
  known gap" slices from the post-roadmap planning session
  (`chat-rate-limiting`, `achievement-system`, this one), before
  extended observability. `admin-content-authoring` had shipped a role +
  full course/lesson CRUD API, but nothing in the frontend ever consumed
  it — content could only be authored via curl/Postman. This closes that
  gap.
- `GET /api/users/me` gains a `role` field (passthrough — `User` was
  already loaded in full). `AuthContext` fetches it via one follow-up
  call to `/api/users/me` right after login/register succeeds, and
  persists it in `localStorage` alongside `token`/`username` so it's
  available synchronously on every later page load.
- New `RequireAdmin` route wrapper (mirrors `RequireAuth`, redirects
  non-admins to `/courses` instead of `/login`); an "Admin" nav link
  shown only for admins; admin pages for course and lesson list/create/
  edit/delete, routed at `/admin/courses` and `/admin/courses/:courseId/
  lessons`.
- **Scope decision, made before writing any code:** lesson content
  (grammar points, dialogue, culture note — three levels of nesting) is
  edited as a single JSON `<textarea>` with client-side `JSON.parse`
  validation before submit, not a bespoke add/remove/reorder nested-list
  form builder. A full form-builder for that shape would have been the
  largest UI surface in the app for content only one person (the admin)
  ever touches — documented explicitly in the proposal's cut line rather
  than silently simplified.
- `api/client.ts` gained `put`/`delete` (only `get`/`post`/`patch`
  existed before). Delete confirmation is a first-of-its-kind inline
  Confirm/Cancel swap, not `window.confirm()` — chosen so it stays
  scriptable/testable via browser automation and styleable to match the
  rest of the app.

**Errors & lessons**
- *Recurring browser-automation click-timing flakiness, twice in this
  session's verification:* (1) granting a test user `ADMIN` via direct
  SQL and logging in again initially showed the OLD `USER` role in
  `localStorage` — turned out to be a stale click on "Log out"/"Log in"
  that hadn't actually completed before subsequent actions ran, not a
  bug in the new role-persistence code. Confirmed by clearing
  `localStorage` directly via `javascript_tool` and redoing the login
  deliberately, which then correctly showed `role: "ADMIN"`. (2) A
  malformed-JSON test initially showed no effect because a click into
  the lesson-content textarea raced the page's async content-loading
  `useEffect`, so the typed text landed then got overwritten. Fixed by
  waiting for the page to fully settle before interacting — both were
  test artifacts, not app bugs, confirmed by cross-checking against the
  actual API response and network tab in each case.
- Verified the malformed-JSON guard by inspecting network traffic, not
  just the UI: submitting invalid JSON showed the inline error and the
  browser's network tab confirmed zero `/api/` requests fired — the same
  "confirm the specific behavior the design doc calls out, don't just
  eyeball it" discipline used throughout this session.
- Found an existing `ADMIN` account in production from
  `admin-content-authoring`'s original manual-grant verification
  (`demo-rehearsal-...@example.com`) with no recorded password —
  registered and granted a fresh test account for this slice's
  production spot-check instead of trying to recover it.

## 2026-08-01 — `achievement-system`: streak and lesson-completion badges

**Shipped**
- OpenSpec change `achievement-system` — second post-roadmap slice, chosen
  for being the one with genuine new architectural texture (derived,
  multi-hop state) versus the other three options (admin UI, extended
  observability) which are hardening/ops work on existing surfaces.
- `event-worker` evaluates a fixed catalog of 6 achievements (3/7/30-day
  streaks, 1/3/5-lesson completions) inline whenever it processes an
  `exercise.completed` event — no new Kafka topic or consumer, since the
  streak/completion state it needs already lived there. Evaluation itself
  is a pure function (`internal/achievements.Evaluate`), mirroring the
  existing `internal/streak` package's unit-testable shape.
- New `GET /api/users/me/achievements` (proxied from a new event-worker
  endpoint) returns the full catalog with locked/unlocked status; new
  `AchievementsPage` badge grid in the frontend.

**Errors & lessons**
- *Found while reading `store.go`, before writing any code:* the existing
  day-granularity `daily_activity` guard (used for streak updates) can't
  also gate completion counting. If a user completes two different
  lessons on the same calendar day, the second event hits that guard's
  early-return branch — so completion counting placed after it would
  silently drop the second lesson. Fixed by giving completion counting
  its own idempotency table (`lesson_completions`, keyed on `(user_id,
  lesson_id)`), independent of the day guard. Verified directly: completed
  3 lessons in one UTC day and confirmed all 3 were counted (not 1).
- *Local Kafka consumer-group flakiness recurred* — the same issue
  `profile-and-progress` hit: a fresh test user's `user.registered` and
  first `exercise.completed` events sat unconsumed in the local
  docker-compose stack (event-worker's consumer group appeared stuck).
  Restarting the `event-worker` container made it rejoin and process the
  backlog cleanly. Confirmed this is purely a local-dev artifact — the
  identical flow on production unlocked the achievement immediately with
  no restart needed.
- *Kafka redelivery idempotency verified directly*, not just reasoned
  about: manually re-published a lesson's exact `exercise.completed`
  payload via `kafka-console-producer` and confirmed no duplicate
  `lesson_completions` row and no unlock-timestamp change.

## 2026-07-31 — `chat-rate-limiting`: capping the one unbounded LLM endpoint

**Shipped**
- OpenSpec change `chat-rate-limiting` — first of the post-roadmap slices
  (chosen over an achievement system, an admin UI, and extending
  observability). `/api/conversation/reply` was the one AI-backed
  endpoint with no cap; exercise generation didn't need one because
  `ExerciseService.listByLesson` checks Postgres first and short-circuits
  permanently once a lesson has any `Exercise` rows, capping its lifetime
  LLM-call surface at the fixed lesson count.
- New `RateLimiter` component (`core-api/.../security/RateLimiter.kt`):
  in-memory, per-user, fixed-window counter keyed by `"chat:${user.id}"`,
  10 requests/minute, synchronized per-key rather than globally. Deliberately
  not a `Filter`/`HandlerInterceptor` — inline in `ConversationController`,
  since there's exactly one call site and a generic framework would be
  over-engineering. No Redis — single replica, in-memory state is fine.
  Took an injected `Clock` bean (new, added to `HanaTalkApplication`) so
  the window-elapsed logic is testable without `Thread.sleep`.
- Frontend (`ChatPage.tsx`): catches a 429 specifically
  (`err instanceof ApiError && err.status === 429`) and shows "You're
  sending messages too fast — wait a moment and try again," preserving
  the typed message in the input either way.

**Errors & lessons**
- *Sequential curl testing looked like the limiter didn't work* — 11
  requests fired one after another all returned 200. Root cause: each
  real chat call takes 6-30+ seconds (genuine LLM latency), so 11
  sequential requests span well past the 60-second window and it resets
  naturally before 10 can ever accumulate inside it. Not a bug — a test
  methodology flaw. Firing 15 requests **concurrently** instead produced
  exactly 10×200 + 5×429, matching the configured limit precisely. Same
  concurrent-request pattern re-verified cleanly against production
  after deploy (10×200 + 1×429 on the 11th).
- *429 response body has no "message" field* — confirmed this is a
  pre-existing Spring Boot default (message omitted unless
  `server.error.include-message=always` is set), not something this
  change introduced: an unrelated, already-shipped 404 from
  `VocabularyReviewController` has the identical shape. Harmless here —
  the frontend keys off `err.status`, not the response body's message.

## 2026-07-30 — `audio-pronunciation`: slice 5, the last slice

**Shipped**
- OpenSpec change `audio-pronunciation` — slice 5, completing all five
  slices of the deepening-interactivity plan first laid out in
  `structured-lesson-content`'s proposal. A 🔊 speaker button next to
  every vocabulary row, grammar-point example sentence, and the current
  flashcard, reading the Japanese text aloud via the browser's built-in
  `speechSynthesis` API (`lang: 'ja-JP'`, `rate: 0.85`). Entirely
  client-side — no backend, no new dependency, no database change; the
  cheapest slice by design, sequenced last specifically because it had
  nothing to attach to before the vocabulary table (`structured-lesson-
  content`) and flashcard page (`vocabulary-review`) existed.
- One shared `speak()`/`isSpeechSupported()` utility
  (`frontend/src/lib/speech.ts`) and one `SpeakButton.tsx` component,
  reused in all three places rather than three separate implementations.
  `speak()` always calls `speechSynthesis.cancel()` before `speak()`, so
  rapid clicking switches to the newest text instead of queuing stale
  audio behind it.

**Errors & lessons**
- *This dev sandbox has zero TTS voices installed* —
  `speechSynthesis.getVoices().length === 0` even though
  `'speechSynthesis' in window` is `true`. Verified the implementation is
  correct at the API level anyway: calling `speechSynthesis.speak()`
  directly with the same `lang`/`rate` throws no exception and the
  utterance carries the right values, and monkey-patching
  `speechSynthesis.cancel` to count calls confirmed the cancel-then-speak
  behavior fires exactly once per click. What couldn't be verified in
  this environment — actual audible output — is a genuine environment
  gap (no OS-level TTS engine), not a code defect; worth listening for
  real audio during the production spot-check on a normal browser/OS
  instead.

**Roadmap note:** this closes out the original five-slice plan
(`structured-lesson-content`, `new-exercise-types`, `ai-conversation-
practice`, `vocabulary-review`, `audio-pronunciation`), plus the
interleaved `profile-and-progress` slice that wasn't in the original plan.

## 2026-07-30 — `vocabulary-review`: slice 4, Leitner-style flashcards

**Shipped**
- OpenSpec change `vocabulary-review` — slice 4 of the deepening-
  interactivity plan. A `/flashcards` page reviews vocabulary from
  lessons the user has actually completed, on a schedule that adapts to
  whether they got it right: correct doubles the interval (capped at 90
  days), incorrect resets it to 1 day. This is the payoff for the
  decision made back in `structured-lesson-content` to keep
  `vocabulary_items` a real table instead of JSON — this slice needed
  zero schema changes to that table, only a new one alongside it.
- New `user_vocabulary_progress` table (`V13`), composite-keyed
  (`user_id`, `vocabulary_item_id`) mirroring `user_lesson_progress`'s
  `UserLessonProgressId` pattern — but the first genuinely *mutable*
  per-user table in the codebase; every other one (`user_lesson_progress`,
  `exercise_attempts`) is an append-only log.
- The due-queue query is plain Kotlin composition (completed lesson IDs →
  their vocabulary → filter by due-or-never-reviewed), not a hand-written
  JPQL join — matches the codebase's existing preference for simple
  derived queries over query-string logic.
- New `VocabularyReviewController` (separate from the existing, public,
  lesson-scoped `VocabularyController` — different resource shape and
  auth story, not worth conflating).
- Frontend: `FlashcardsPage.tsx` shows one card at a time, Japanese-only
  until revealed, then correct/incorrect buttons that submit and advance
  locally without a refetch. An explicit "nothing due" state instead of
  a blank card area when the queue is empty or exhausted.

**Errors & lessons**
- *A mockito-kotlin `any()` pitfall*: `whenever(repo.save(any()))` without
  a type parameter didn't match `save`'s generic `<S extends T> S
  save(S)` signature, so the stub silently never applied — every review
  in the service test threw a Kotlin null-check `NullPointerException`
  ("save(...) must not be null") on the real (un-stubbed, therefore
  null-returning) mock. Fixed with an explicit `any<UserVocabularyProgress>()`,
  matching the working precedent already in `ExerciseServiceTest`
  (`any<List<Exercise>>()` for `saveAll`) — worth remembering as a
  recurring Kotlin/Mockito interaction, not a one-off mistake.
- *Verified the scheduling math against the database directly*, not just
  the UI, continuing the practice established during
  `ai-conversation-practice`'s JLPT-level debugging: after marking one
  card correct and one incorrect, `SELECT ... FROM user_vocabulary_progress`
  confirmed exactly the expected `interval_days`/`correct_streak` pairs
  for each, rather than trusting that the UI advancing to the next card
  meant the write had actually happened correctly.

## 2026-07-30 — `ai-conversation-practice`: slice 3, free-form chat with an LLM tutor

**Shipped**
- OpenSpec change `ai-conversation-practice` — slice 3 of the
  post-roadmap deepening-interactivity plan, and the first practice
  surface in the app that isn't a closed-form exercise type. A learner
  converses in Japanese with an LLM tutor on a new `/chat` page and gets
  a correction when they make a real mistake.
- `ai-exercise-svc`'s three-provider fallback loop (Gemini → Groq →
  OpenRouter) was extracted from `generate_exercises` into a shared
  `app/llm_fallback.py::call_with_fallback(prompt, schema, parse)` —
  the `parse` callback preserves the exact existing behavior where a
  schema-invalid response (not just a transport error) still counts as
  that provider failing and falls through, verified by re-running all of
  `test_generation.py` unchanged (its behavior, not the loop, is what the
  tests actually assert) after retargeting the `patch()` calls to the new
  module. New `app/chat.py` reuses the same helper for a new purpose.
- The chat reply schema (`japanese`/`english`/`correction`) deliberately
  mirrors `LessonContent.Dialogue`'s `DialogueLine` shape rather than
  inventing something new.
- core-api: no new client bean — `getChatReply` was added to the
  existing `AiExerciseSvcClient` (both `/generate` and `/chat` target the
  same downstream service; a second client would just duplicate the
  `RestClient.Builder`/timeout wiring). New `ConversationController`
  derives the JLPT level server-side from `user.startingLevel ?? N5`
  rather than trusting the client — reusing the same field the
  `profile-and-progress` slice made editable.
- Frontend: new `ChatPage.tsx`. A `pending: string | null` state (rather
  than optimistically appending to the transcript and rolling back on
  failure) holds the in-flight message as a distinct "sending…" bubble;
  the user turn and tutor reply only commit to the transcript together,
  on success. On failure, the typed message is restored to the input for
  a no-retyping retry.
- No persistence anywhere — conversation history lives only in
  `ChatPage`'s React state for the page's lifetime, per the original
  slice plan.

**Errors & lessons**
- *The JLPT-level prompt only worked as a ceiling, not a real scale.*
  "Keep your Japanese at or below {jlpt_level}" does nothing useful for
  N1 (the top of the scale) — there's no "below" to constrain toward, so
  the model defaulted to simple, all-hiragana replies regardless of
  level. A first fix (framing it as matching the level in both
  directions) wasn't strong enough either. What actually worked: concrete
  worked examples of what each level's output should look like inline in
  the prompt (see `_PROMPT_TEMPLATE` in `app/chat.py`) — after that, an
  N1-level conversation used genuinely adult vocabulary (複雑, 円安,
  物価高騰) instead of hiragana-only phrasing for the same question. Same
  lesson as `new-exercise-types`' exercise-type-diversity prompt: LLM
  instruction-following for style/register is probabilistic, not
  guaranteed, and vague ceiling framing is weaker than concrete examples.
- *A UI/automation false alarm almost hid the above finding.* The first
  attempt to set the test account's JLPT level to N1 via `/profile`
  silently didn't persist — confirmed the bug was in the click, not the
  product, by checking `SELECT starting_level FROM users` directly and
  finding it still null after the "successful" click. Redid it, confirmed
  N1 actually landed in the database this time, and only then was the
  prompt-quality comparison actually valid. Worth remembering: a browser-
  automation action reporting success doesn't mean the underlying app
  state changed — check the actual data when a test result seems to
  contradict what the code should do.

## 2026-07-30 — Lesson prev/next navigation + jump-to-lesson index

Small, direct fix (no OpenSpec proposal — flagged earlier as a UI gap not
worth full ceremony for). `LessonPage` only ever had a "back to course"
link; getting to the next lesson meant navigating all the way back to the
course page. `LessonPage` now also fetches the course's lesson list
(already available, same endpoint `CourseDetailPage` uses) to compute the
current lesson's position among its siblings: a prev/next card pair at the
bottom of the lesson content (only rendering whichever side exists — first
lesson shows just "Next", last shows just "Prev"), and a "Jump to lesson"
toggle in the header that expands the same `.lesson-list`/`.lesson-row`
markup `CourseDetailPage` already uses (including completion checkmarks),
so the two pages look consistent for free. Verified live: navigating
prev/next lands on the correct sibling lesson, the index highlights the
current lesson and shows real completion state, and the edge cases (first
lesson has no prev card, last has no next card) both render correctly.

## 2026-07-30 — `profile-and-progress`: shipping the streak/leaderboard UI

**Shipped**
- OpenSpec change `profile-and-progress` — pure frontend work, no backend
  changes: every endpoint it needed (`GET /api/users/me`,
  `PATCH /api/users/me/level`, `GET /api/users/me/streak`,
  `GET /api/leaderboard`) already existed and was already deployed, just
  never had a UI. New `ProfilePage` (username, JLPT level with an editable
  `<select>` + Save, current streak) and `LeaderboardPage` (ranked list,
  the signed-in user's own row visually distinguished by matching
  `username` — the only identity the frontend already holds client-side
  via `useAuth()`, no `userId` round trip needed). Header username is now
  a link to `/profile`.
- Discovered mid-implementation that the tasks.md draft assumed named API
  wrapper functions in `client.ts` (`getProfile`, `setLevel`, etc.) —
  checked first and found every existing page calls `api.get`/`api.patch`
  directly inline, no wrapper functions exist anywhere in the codebase.
  Followed the actual convention instead of introducing a new one.

**Errors & lessons**
- *Local `event-worker` Kafka consumer got stuck rebalancing.* Verifying
  the "highlight my own row" behavior needed real streak data, so I
  registered a user and completed a lesson. `core-api` published both
  `user.registered` and `exercise.completed` correctly (confirmed via
  `kafka-console-consumer` reading the raw topic), but
  `kafka-consumer-groups.sh --describe --group event-worker` reported
  "rebalancing" indefinitely and zero committed offsets — a restart of
  the `event-worker` container didn't fix it either. This is a
  pre-existing local-environment quirk (this session's `docker compose up`
  had never had a working event-worker consumer before this point),
  unrelated to this change's code — production's event-worker has
  consumed correctly in every prior session's verification. Worked
  around it for verification purposes only by seeding a row directly in
  `event_worker.user_streaks` (local dev Postgres, not production) to
  confirm the frontend rendering itself was correct. Not investigated
  further — out of scope for a frontend-only change; worth a fresh look
  if it recurs.
- *Browser-automation `ref`-based clicks on React Router `<Link>` elements
  intermittently didn't trigger navigation*, while the exact same click
  by pixel coordinate worked every time, and `ref`-based clicks on
  `<button>` elements worked fine. Concluded this is a browser-automation
  tooling quirk, not a product bug (real navigation via `navigate()` and
  coordinate clicks both worked reliably) — noting it in case it recurs
  in future verification sessions.
- *A false-alarm "level doesn't persist" during production verification*:
  reading the profile page's state immediately after `navigate()` reloaded
  it showed the level reverted to N5, which looked like the save hadn't
  persisted. It had — `ProfilePage`'s `selectedLevel` state initializes to
  `'N5'` and only updates once its own `GET /api/users/me` fetch resolves,
  so reading page state before that async fetch completes always shows
  the default, regardless of what's actually stored. Confirmed real
  persistence by waiting ~2s after navigating before reading state, and
  independently via `read_network_requests` showing the `PATCH` returning
  200. A reminder to wait for a page's own data-fetch to settle before
  treating its rendered state as ground truth, not just wait after the
  action that changed the data.

## 2026-07-30 — Architecture planning: CQRS, Kafka scope, and the next slice

**No code shipped this entry — planning + docs only.**

User raised a former colleague's suggestion to adopt CQRS + event sourcing
(from a library he'd built at UKG), and separately flagged that Kafka felt
underused ("only for the leaderboard and streak reads... isn't that
superficial?") and asked where the leaderboard actually was, since it
wasn't visible in the frontend. Also flagged two UX gaps found while
clicking around: no next/previous lesson navigation, and no user
settings/profile page.

Had an Explore agent audit the actual state before answering (didn't want
to guess): confirmed `GET /api/leaderboard` and `GET /api/users/me/streak`
are both fully implemented and deployed, but **called from nowhere in the
frontend** — zero routes, zero components. `PATCH /api/users/me/level`
is the same story. So the "is Kafka superficial" instinct was right, but
the actual cause wasn't thin plumbing — the two-topic
(`user.registered`/`exercise.completed`) event-worker setup is solid,
idempotent, well-tested — it's that nothing built on top of it ever
shipped a UI.

On the CQRS+ES question: concluded the useful half (lean, purpose-built
read models instead of loading a full aggregate for a query) is already in
production via `event-worker`'s streak/leaderboard tables, and declined
full event sourcing as disproportionate to this domain. Full reasoning now
lives in `docs/ARCHITECTURE.md` §6 (new section) rather than here.

**Decided:** propose a new slice, `profile-and-progress` (streak badge,
leaderboard page, JLPT-level setting), ahead of the already-planned slice 3
(AI conversation practice) — cheap, and converts already-deployed backend
work into something demoable rather than adding new build scope. The
lesson prev/next + index gap is small enough to fix directly later,
doesn't need a proposal. An achievement/milestone system (events reacting
to other events, not just flat projection) was discussed as a possible
future slice, lower priority. Full decision recorded in `docs/ROADMAP.md`'s
2026-07-30 entry.

## 2026-07-30 — Production incident: `new-exercise-types` rollout verification

**What happened**

Verifying `new-exercise-types` in production (task 5.2) uncovered a chain of
three separate, real bugs — not one:

1. **Stale MongoDB cache from UUID reuse.** Both `V11` and `V12` reused the
   same lesson UUIDs across full content replacements, but `ai-exercise-svc`'s
   `generated_exercises` Mongo cache is keyed only by `lesson_id`, with no
   content-based invalidation. It kept serving exercises generated against
   old, now-deleted lesson content. Cleared via a throwaway pod
   (`kubectl run --rm -it --image=mongo:8 ... mongosh ... --eval
   "db.generated_exercises.deleteMany({})"`) connecting to the `mongo`
   Service over the network — **not** an interactive `mongosh` session inside
   `mongo-0` itself, which is discussed below.
2. **Stale Postgres rows masking the Mongo fix.** `ExerciseService.listByLesson`
   checks `exerciseRepository.findByLessonId` *first* and returns immediately
   if any rows exist — it never even reaches the Mongo cache once exercises
   are persisted. An earlier verification request had hit the stale Mongo
   cache and gotten those results permanently written to Postgres, so
   clearing Mongo alone did nothing; the Postgres rows for lesson 1 also had
   to be deleted by hand before a fresh generation could happen at all.
3. **The real bug: one malformed exercise failed the whole batch.**
   `SENTENCE_ORDERING`'s prompt didn't force the LLM to keep `correct_answer`
   in the same tokenization/script as `options` (it sometimes romanized
   `correct_answer` while `options` stayed in kana/kanji). `GenerationResult`
   validated the whole batch as a single Pydantic model, so one malformed
   item invalidated *everything*, forcing a fall-through to the next
   provider. When all three providers made the same mistake, the request
   died after ~90s (core-api's `AI_EXERCISE_SVC_TIMEOUT_SECONDS` default)
   with zero exercises generated — surfacing to users as a 502. Fixed by
   validating exercises individually in `generate_exercises` (parse raw
   JSON, keep only the schema-valid items, then run the batch-level minimum-
   variety check against the survivors) instead of failing the entire
   response over one bad item. Also tightened the prompt's sentence-ordering
   instructions to say `correct_answer` must reuse `options`' strings
   verbatim. Verified live afterward: lesson 1 returned 7 exercises in 32s
   with a genuine mix of all four types.

**Lesson for later slices:** the lesson-UUID-reuse-across-content-replacement
pattern (used by both `V11` and `V12`) is a latent trap for any future
per-lesson cache keyed only by ID — worth a content hash or version field in
the cache key if this recurs (e.g. for Slice 4's spaced-repetition tables).

**Errors & lessons**
- *Never run an interactive process inside `mongo-0`'s own container.* Its
  resources are deliberately capped tight (`limits: memory: 512Mi`,
  `--wiredTigerCacheSizeGB=0.25`, see `infra/k8s/mongo/mongo.yaml`). Running
  `mongosh` interactively via `kubectl exec -it mongo-0 -- mongosh` puts the
  Node.js mongosh process in the *same* cgroup as `mongod` itself — it pushed
  memory over the limit and OOM-killed the whole container (confirmed via
  `RESTARTS: 1` and readiness/liveness-probe-failed events in `kubectl
  describe pod mongo-0`). Recovered cleanly on its own (data safe on the
  PVC), but the fix is to always use a separate throwaway pod for one-off
  Mongo commands, never `mongo-0` itself.
- *A `kubectl run ... -- mongosh ... --eval "..."` command pasted across two
  lines in fish silently split into two commands* (no trailing backslash),
  producing a confusing pair of errors (`connection refused` from the first
  truncated command, `Unknown command: --restart=Never` from the second).
  Always paste multi-flag `kubectl` one-liners as a single unbroken line.

## 2026-07-29 — New exercise types: translation and sentence-ordering

**Shipped**
- OpenSpec change `new-exercise-types` — second slice of the post-roadmap
  content/interactivity plan. Every exercise since M3 had been MCQ or
  fill-in-the-blank; `TRANSLATION` and `SENTENCE_ORDERING` are new, added
  independently to both `ExerciseType.kt` (core-api) and `app/schemas.py`
  (ai-exercise-svc) — the two services still share no schema, a known,
  accepted duplication since M3.
- `ExerciseService.grade()` (a single exhaustive `when`, no `else` — the
  compiler forces handling every case) gained two branches: `TRANSLATION`
  grades identically to `FILL_IN_BLANK` (trim + lowercase); `SENTENCE_ORDERING`
  grades as an exact match after trim, case-sensitive, since word order and
  exact tokens both matter for that type.
- No new database column: `SENTENCE_ORDERING` reuses `Exercise.optionsJson`
  for the shuffled word tokens and `correctAnswer` for the correctly-ordered
  tokens space-joined — the same two generic fields every other exercise
  type already uses, not a new column just for one type.
- `ai-exercise-svc`'s prompt now asks for a mix of all four types across a
  batch, with format instructions for each. Deliberately did **not** make
  the new types a hard validator requirement (still just ≥1 MCQ, ≥1
  FILL_IN_BLANK, ≥4 total) — a hard 4-type floor risked making generation
  fail more often on weaker fallback providers for lessons with few grammar
  points. Verified live: a real generation call for a 7-point lesson still
  produced a genuine mix (4 MCQ / 3 fill-in-blank / 3 translation / 2
  sentence-ordering, 12 exercises total) purely from prompt guidance, no
  hard requirement needed.
- Frontend: `TRANSLATION` needed zero new code — it already falls into the
  existing no-`options` text-input branch `FILL_IN_BLANK` uses.
  `SENTENCE_ORDERING` got a new `SentenceOrderingInput` component: click a
  shuffled token to append it, click a picked token to send it back —
  handles repeated tokens (e.g. です appearing twice in a sentence) by
  removing one matching occurrence per pick rather than filtering by value,
  which would have removed all copies at once.

**Errors & lessons**
- *A real bug caught by my own browser-testing mistake, not hidden by it*:
  live-verifying the sentence-ordering UI, I submitted after picking only 6
  of 7 tokens (clicked one too few before hitting Submit) — the exercise
  correctly graded it "not quite," which is exactly the right behavior for
  an incomplete answer. Worth noting only because it's a clean example of
  the grading logic doing its job under a genuine (if accidental) edge
  case, not a rehearsed happy path.


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
