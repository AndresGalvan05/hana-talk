## Context

All four endpoints this change consumes already exist and are unchanged:

- `GET /api/users/me` → `{ username, nativeLanguage, startingLevel }`
- `PATCH /api/users/me/level` (body `{ level }`) → same shape, updated
- `GET /api/users/me/streak` → `{ userId, currentStreak, lastActiveDate }`
- `GET /api/leaderboard` → `[{ userId, username, currentStreak }]`, ranked
  highest-streak-first, capped at 20 entries by `event-worker`

The frontend has no state-management library beyond `AuthContext` (React
Context wrapping a JWT + username in `localStorage`) and a thin `api.get`/
`api.post`/`api.patch` wrapper (`frontend/src/api/client.ts`). Every
existing page (`CoursesPage`, `CourseDetailPage`, `LessonPage`) fetches its
own data with a local `useState`/`useEffect` pair — no query-caching
library, no global data store. See `proposal.md` for why this work exists.

## Goals / Non-Goals

**Goals:**
- Add `ProfilePage` and `LeaderboardPage`, following the same
  fetch-in-`useEffect` pattern as every other page, with zero new
  dependencies.
- Make both reachable from the site header.

**Non-Goals:**
- No new state-management or data-fetching library — would be
  inconsistent with every other page and unjustified for two more
  single-fetch pages.
- No client-side caching/revalidation of streak or leaderboard data beyond
  "fetch once when the page mounts" — matches how `CoursesPage` already
  behaves, and neither value needs to feel real-time.

## Decisions

- **"Is this me?" on the leaderboard is matched by username, not
  `userId`.** The frontend never learns the authenticated user's id today
  — `AuthContext`/`tokenStore` only ever stored a username since login
  (`frontend/src/api/client.ts`'s `USERNAME_KEY`). Registration enforces
  unique usernames (`RegisterRequest`'s `@Size(min=3,max=30)` plus a DB
  uniqueness constraint), so comparing `LeaderboardEntryDto.username`
  against the already-available `tokenStore.getUsername()` is sufficient
  and needs no extra request. Alternative considered: call
  `GET /api/users/me` first to get nothing username doesn't already give
  us — rejected, it would only add a redundant round trip.
- **Level editing is a plain `<select>` of the five `JlptLevel` values plus
  a Save button**, not an always-editable inline field. Keeps the "editing"
  state explicit and matches the project's existing form conventions
  (`RegisterPage`'s controlled inputs) rather than introducing a new
  inline-edit pattern for one field.
- **New CSS rules go into the single global `frontend/src/index.css`**,
  reusing the existing `.card` class where layout allows — the project has
  no CSS modules and every prior slice (vocabulary table, grammar cards,
  sentence-ordering tokens) followed this same convention.
- **Both new routes sit inside the existing authenticated `Layout` route
  tree** (`/profile`, `/leaderboard`, alongside `/courses`), not as
  standalone routes — both require a signed-in user, exactly like every
  existing page.

## Risks / Trade-offs

- **Leaderboard ranks by streak only, no secondary metric (total lessons,
  total exercises)** → Accepted per `proposal.md`'s cut line; matches what
  `event-worker` actually computes today. Adding a new metric is a backend
  change, out of scope here.
- **No pagination UI for the leaderboard** → Not a new gap: `event-worker`
  already caps the endpoint at 20 entries, so there's nothing to paginate
  against yet.
- **Username-based self-matching would break if usernames stopped being
  unique** → Not a new risk this change introduces; uniqueness is already
  an existing registration invariant this design simply relies on rather
  than re-validates.

## Migration Plan

Pure frontend addition — no database migration, no backend deploy, no
feature flag. Ships through the existing CI → GHCR → `kubectl rollout
restart deployment/frontend` path used by every prior frontend change.
Rollback is the same as any other frontend deploy: restart back to the
previous image tag.
