## Why

The streak and leaderboard backend has been live since M4 (`event-worker`,
Kafka-driven, consuming `user.registered`/`exercise.completed`) but has
**zero frontend surface** — no route or component anywhere in `frontend/`
calls `GET /api/users/me/streak` or `GET /api/leaderboard`. The same is
true of user settings: `GET /api/users/me` and `PATCH /api/users/me/level`
are both implemented and unused. Shipping this UI converts already-deployed
backend investment into an actual, demoable feature, and it's cheap: every
endpoint this slice needs already exists and is stable — this is pure
frontend work, no backend changes.

## What Changes

- New `/profile` route: shows username and current JLPT level, with an
  editable control for level backed by the existing
  `PATCH /api/users/me/level`, and the user's current streak from
  `GET /api/users/me/streak`.
- New `/leaderboard` route: ranked list from `GET /api/leaderboard`
  (current streak, highest first), with the signed-in user's own row
  visually distinguished if they appear in it.
- Site header (`Layout.tsx`) gains a link to the profile page next to the
  existing username/logout controls, so both new pages are discoverable
  without typing a URL.
- No backend changes of any kind — all four endpoints involved
  (`GET /api/users/me`, `PATCH /api/users/me/level`,
  `GET /api/users/me/streak`, `GET /api/leaderboard`) are unchanged by
  this slice.

## Capabilities

### New Capabilities
- `profile-progress-ui`: the frontend profile page (level display/edit,
  streak display) and leaderboard page.

### Modified Capabilities
(none — the underlying API contract in `streaks-leaderboard` and the
existing, previously-unspecified `UserProfileController` endpoints are
consumed as-is, with no requirement-level change)

## Impact

- `frontend/`: new `ProfilePage.tsx`, `LeaderboardPage.tsx`, two new
  routes in `App.tsx`, a nav link in `Layout.tsx`, new API client
  functions in `frontend/src/api/client.ts`, and any missing response
  types in `frontend/src/api/types.ts` (`UserProfile` already exists;
  streak/leaderboard entry types are new).
- No changes to `core-api`, `ai-exercise-svc`, `event-worker`, or `infra/`.

## Non-goals / cut line

- No new backend capability (e.g. an achievement/milestone system reacting
  to streak or completion thresholds) — discussed as a possible future
  slice, explicitly out of scope here.
- No lesson prev/next/index navigation — a separate, unrelated UI gap
  found in the same conversation, tracked and fixed independently of this
  change.
- No leaderboard metric beyond current streak (e.g. total lessons or
  exercises completed) — `event-worker`'s schema only tracks streak
  today; adding new metrics is a backend change and out of scope.
- No avatar, display-name change, password change, or account deletion —
  scope stays to what's already backed by an existing endpoint (JLPT
  level) plus existing read-only progress data (streak, leaderboard).

## Milestone

Post-roadmap slice (M1–M5 are complete). Continuation of the
deepening-interactivity work started by `structured-lesson-content` and
`new-exercise-types` — proposed ahead of the already-planned slice 3 (AI
conversation practice) per the 2026-07-30 `docs/ROADMAP.md` decision log
entry, since it surfaces already-shipped backend work rather than adding
new build scope.
