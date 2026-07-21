## Why

M3 steps 1–3 (all archived) built exercise generation, grading, and provider
failover entirely server-side — a user can already `GET
/api/lessons/{id}/exercises` and `POST /api/exercises/{id}/attempts` with
curl, but the frontend has no UI for either endpoint. This was deliberately
deferred (see the `ai-exercise-svc` proposal's decision log) until real
generated content existed to build against, rather than building UI for
placeholder seed data. It now does, across all three providers.

## What Changes

- `LessonPage` gains a new "Practice exercises" section, fetched alongside
  the existing lesson/progress fetches, rendering each exercise as a small
  quiz card: MCQ as a radio-button group, fill-in-blank as a text input,
  each with its own submit button and local correct/incorrect feedback.
- A correct attempt on any exercise re-fetches course progress the same way
  the existing "Mark as complete" button already does, reusing the exact
  same `completed`/`progress` state and success banner — no new completion
  UI is introduced, exercises just drive the same state transition through
  a callback.
- Incorrect attempts allow immediate retry (multiple attempts per exercise
  are already recorded server-side; the UI doesn't block resubmission).
- The exercises fetch has an explicit "still working" loading state,
  because a lesson's *first-ever* request can take up to ~90s (provider
  fallback chain) — subsequent requests for the same lesson are fast
  (`Exercise` rows already persisted). The loading copy is honest about
  this without alarming users on the common fast path (a delayed message
  upgrade after a few seconds, not an indefinite spinner).
- A failed exercises fetch (e.g. the whole provider chain exhausted) shows
  a friendly error with a manual retry button — retrying is always safe
  (idempotent on the backend).
- The existing "Mark as complete" button and manual-completion path are
  untouched — exercises are an additional path to completion, not a
  replacement.

## Capabilities

### New Capabilities
- `exercise-practice-ui`: the frontend's lesson-page exercise practice
  experience — fetching, rendering, answering, and retrying exercises, and
  how a correct answer reflects into the existing completion state.

### Modified Capabilities
(none — no backend API changes; `exercise-grading` and
`exercise-generation`'s existing contracts are consumed as-is)

## Impact

- **`frontend/src/pages/LessonPage.tsx`**: adds a new child section/
  component and one more fetch; existing `completed`/`progress`/`error`
  state and the manual-complete button are unchanged.
- **`frontend/src/api/types.ts`**: new `Exercise`/`AttemptResult`
  interfaces mirroring core-api's `ExerciseResponse`/`AttemptResponse` DTOs.
- **`frontend/src/index.css`**: new classes for the exercise cards,
  following the existing plain-CSS, semantic-class-name convention (no
  CSS modules, no component library).
- **No backend changes** — core-api and `ai-exercise-svc` are consumed
  exactly as they already exist.
- **Non-goals / cut line**: no automated frontend tests (the project has
  none today for any page — verification stays manual via the Playwright
  MCP server, per `CLAUDE.md`); no hiding/gating the practice section once
  a lesson is already complete (harmless to still show and retry); no
  progress bar/animation during generation, just honest loading copy; no
  exercise editing, regeneration trigger, or admin UI; no offline
  queueing or automatic polling — a failed fetch requires a manual retry
  click.
- **Milestone**: M3, step 4 (final step) of `docs/ROADMAP.md`'s "Order
  inside milestone" — completes M3.
