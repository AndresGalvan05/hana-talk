## 1. Types and API wiring

- [x] 1.1 Add `Exercise` and `AttemptResult` interfaces to
      `frontend/src/api/types.ts`, mirroring core-api's
      `ExerciseResponse`/`AttemptResponse` DTOs (`id`, `lessonId`, `type`,
      `prompt`, `options: string[] | null`; `correct: boolean`)
- [x] 1.2 Confirm `api.get<Exercise[]>` / `api.post<AttemptResult>` work
      as-is against the existing `/api/lessons/{lessonId}/exercises` and
      `/api/exercises/{exerciseId}/attempts` endpoints (no client changes
      expected — just usage)

## 2. `ExercisePractice` component

- [x] 2.1 Create `frontend/src/components/ExercisePractice.tsx` (or
      `pages/`-local, matching existing file organization) accepting
      `lessonId: string` and `onCompleted: () => void`
- [x] 2.2 Fetch exercises on mount; loading state starts as "Loading
      exercises…" and upgrades to a "first-time generation" message via a
      ~4s `setTimeout` if the fetch hasn't resolved yet
- [x] 2.3 Render each exercise as a card: MCQ as a radio-button group over
      `options`, fill-in-blank as a text input; each card holds its own
      `answer`/`submitting`/`result: boolean | null` state
- [x] 2.4 Submit handler POSTs the attempt, sets that card's `result`, and
      — only when `result === true` — calls `onCompleted()`
- [x] 2.5 Incorrect result allows immediate resubmission (don't disable
      the input/button after an incorrect attempt)
- [x] 2.6 Fetch failure renders an error message with a retry button that
      re-runs the fetch

## 3. `LessonPage` integration

- [x] 3.1 Render `<ExercisePractice lessonId={lessonId} onCompleted={...}
      />` below the existing lesson content/manual-complete section
- [x] 3.2 `onCompleted` reuses the exact same state transition
      `markComplete()`'s success path already performs (re-fetch progress,
      `setCompleted(true)`, `setProgress(...)`) — extract it into a shared
      function called from both places rather than duplicating the logic

## 4. Styling

- [x] 4.1 Add exercise-card classes to `frontend/src/index.css` following
      the existing plain-CSS, semantic-class-name convention (no CSS
      modules, no new dependency)

## 5. Verification

- [x] 5.1 `docker compose up -d --build` + `npm run dev`; via the
      Playwright MCP server (or manual browser), open a lesson with no
      exercises yet, confirm the loading message upgrades after ~4s,
      exercises render once generation completes
- [x] 5.2 Submit an incorrect answer, confirm feedback shows and
      resubmission works; submit the correct answer, confirm the exercise
      shows correct AND the lesson's completion banner appears without a
      page reload
- [x] 5.3 Reload the same lesson; confirm exercises now load fast (already
      persisted) and the completion banner still shows
- [x] 5.4 Confirm the existing manual "Mark as complete" button still
      works unchanged on a lesson with exercises
- [x] 5.5 Force an exercises-fetch failure (e.g. stop `ai-exercise-svc`
      before a lesson's first request) and confirm the error + retry UI
      appears, then recovers once the service is back and retry is clicked
- [x] 5.6 `npm run lint` and `npm run build` green
- [x] 5.7 Update `docs/ROADMAP.md` (M3 fully done), `docs/DEVLOG.md`
      (session entry), `README.md` if the frontend status line needs
      updating
