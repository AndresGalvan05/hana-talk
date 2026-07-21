## Context

`LessonPage.tsx` currently does two parallel fetches on mount (lesson
detail, progress — both via a `cancelled`-flag-guarded `useEffect`, not
`AbortController`), holds `lesson`/`error`/`completing`/`completed`/
`progress` as local `useState`, and renders a manual "Mark as complete"
button that POSTs to `/api/courses/{courseId}/lessons/{lessonId}/complete`
then re-fetches progress and flips `completed` to show a static success
banner. The shared `api` client (`frontend/src/api/client.ts`) is a single
`request<T>()` wrapper with no per-call timeout — `fetch` has none by
default, which matters here since the exercises endpoint can legitimately
take up to ~90s on a lesson's first-ever request (per the
`provider-failover-chain` design, a forced Gemini failure alone measured
~60s before Groq took over). There is no frontend test runner anywhere in
the repo; verification is manual via the Playwright MCP server per
`CLAUDE.md`.

## Goals / Non-Goals

**Goals:**
- Render exercises for the current lesson, let the user answer and submit
  each one, and show correct/incorrect feedback per exercise.
- A correct attempt reflects into the *same* completion state LessonPage
  already renders (the success banner + progress line), without
  duplicating that UI.
- Be honest about the first-request latency without making every load feel
  slow — a fast cache/persisted-row hit (the common case after the first
  user ever opens a lesson) should show only a brief loading flash.
- Keep the manual "Mark as complete" button working exactly as it does
  today — exercises are additive.

**Non-Goals:**
- No automated tests — consistent with the rest of the frontend.
- No hiding the practice section once a lesson is complete.
- No exercise editing/regeneration UI, no admin surface.
- No polling or websocket updates — a correct attempt's response already
  tells the UI locally that a completion likely just happened; the
  subsequent progress re-fetch confirms it.
- No changes to core-api or `ai-exercise-svc`.

## Decisions

**A child component `<ExercisePractice>`, not inlined into
`LessonPage`.** `LessonPage` passes `lessonId` and an `onCompleted`
callback; `ExercisePractice` owns its own fetch/loading/error/exercise-list
state internally. `onCompleted` is exactly the same logic
`markComplete()`'s success path already runs — re-fetch progress, call
`setCompleted(true)` and `setProgress(...)` — passed down as a prop rather
than duplicated, so there is exactly one place that flips the lesson into
"completed" UI regardless of which path (manual button or a correct
exercise attempt) triggered it.

**Each exercise is its own mini state machine, not a wizard.** All
exercises for the lesson render at once (there are only 1–2 per lesson
today), each as an independent card holding its own `answer`/`submitting`/
`result: boolean | null` state. This avoids any cross-exercise
coordination logic and matches the backend's model — every attempt is
independent, multiple attempts are recorded, only a correct one matters.

**Loading copy upgrades after a delay instead of guessing up front.** The
fetch always starts with a plain "Loading exercises…" message. A
`setTimeout` (~4s) flips a second boolean (`slow`) that swaps in "Still
generating — this can take up to a minute the first time." This keeps the
fast, common case (cached/persisted exercises) from looking alarming while
still being honest on a genuine first-generation request. No progress bar
or percentage — there's no way to know real progress, and a fake one would
be worse than honest text.

**Retry is a manual button, not automatic.** A failed fetch (network error
or a 502 from an exhausted provider chain) shows the existing `error`
paragraph style plus a "Try again" button that re-runs the same fetch.
Automatic retry-with-backoff is out of scope — the backend has no
rate-limiting concern here worth protecting against from the frontend, and
manual retry matches the project's existing error-handling style (no
retry logic exists anywhere else in the frontend today either).

**No explicit fetch timeout is added on the frontend.** `fetch` has no
default timeout, so a ~90s worst case simply resolves slowly rather than
erroring client-side — matching the "one attempt, no artificial cutoffs"
philosophy already used for the request itself. If Cloudflare's proxy (in
front of the production cluster) times out an idle connection before 90s,
that would surface as a fetch failure and hit the existing error/retry
path — a known risk (see below), not something this change attempts to
fix, since it would require infra changes out of scope here.

## Risks / Trade-offs

- [Cloudflare's free-tier proxy may time out a ~90s idle request before
  the backend responds] → falls into the existing error/retry path if it
  happens; not fixed in this change (would require an infra-side change,
  e.g. an early "still working" response from core-api, which is a much
  larger change than a UI addition). Flagged as a verification step against
  the live site, not assumed away.
- [No automated tests means a regression here is only caught by manual
  Playwright-MCP verification] → consistent with the rest of the frontend;
  not treated as a gap unique to this change.
- [Rendering exercises unconditionally even when a lesson is already
  complete could feel redundant to a returning user] → accepted trade-off
  (Non-Goals) — the alternative (conditionally hiding) adds branching logic
  for a cosmetic concern with no functional benefit.

## Migration Plan

No data migration — purely additive frontend code, no backend or database
changes. Rollout is the existing frontend CI → GHCR → `rollout restart`
loop; rollback is the existing sha-pin escape hatch, same as any other
frontend change.
