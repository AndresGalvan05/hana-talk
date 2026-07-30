## 1. API client & types

- [ ] 1.1 Add `Streak` and `LeaderboardEntry` types to
      `frontend/src/api/types.ts` (`{ userId, currentStreak, lastActiveDate }`
      and `{ userId, username, currentStreak }`, matching core-api's
      `StreakResponse`/`LeaderboardEntryDto`)
- [ ] 1.2 Add `getProfile`, `setLevel`, `getStreak`, `getLeaderboard`
      functions to `frontend/src/api/client.ts` wrapping
      `GET /api/users/me`, `PATCH /api/users/me/level`,
      `GET /api/users/me/streak`, `GET /api/leaderboard` via the existing
      `api.get`/`api.patch` helpers

## 2. Profile page

- [ ] 2.1 New `frontend/src/pages/ProfilePage.tsx`: fetch profile + streak
      on mount (`useEffect`/`useState`, matching `CoursesPage`'s pattern),
      show username, current JLPT level, and streak count
- [ ] 2.2 Level-change control: a `<select>` of the five `JlptLevel`
      values plus a Save button; on save, call `setLevel` and update local
      state from the response, no page reload
- [ ] 2.3 Handle the zero-streak case explicitly (render "0", not a blank
      or error state)
- [ ] 2.4 Link to the leaderboard page from the profile page

## 3. Leaderboard page

- [ ] 3.1 New `frontend/src/pages/LeaderboardPage.tsx`: fetch the ranked
      list on mount, render as a table/list ordered as returned (already
      highest-streak-first from the API)
- [ ] 3.2 Distinguish the signed-in user's own row by comparing each
      entry's `username` against `tokenStore.getUsername()` (per
      `design.md` — no `userId` available client-side, and usernames are
      unique)
- [ ] 3.3 Empty-state rendering when the leaderboard has zero entries

## 4. Navigation

- [ ] 4.1 Add a profile link to the site header in
      `frontend/src/components/Layout.tsx`, next to the existing
      username/logout controls
- [ ] 4.2 Add `/profile` and `/leaderboard` routes in `frontend/src/App.tsx`,
      inside the existing authenticated `Layout` route tree

## 5. Styling

- [ ] 5.1 Add profile/leaderboard rules to `frontend/src/index.css`
      (reusing `.card` where it fits), including a distinct style for the
      leaderboard's "this is you" row

## 6. Local verification

- [ ] 6.1 `docker compose up -d --build`, `npm run dev`; register a fresh
      user, open `/profile`, confirm username/level/streak render and a
      level change persists (reload the page and confirm it stuck)
- [ ] 6.2 Complete a lesson (or an exercise) to generate real streak
      activity, then confirm `/profile`'s streak and `/leaderboard`'s
      ranking both reflect it, and that user's row is visually
      distinguished on the leaderboard
- [ ] 6.3 `npm run lint` (oxlint) and `npm run build` (`tsc -b && vite
      build`) both green

## 7. Production rollout

- [ ] 7.1 (User-executed) Deploy — merge to `main`, CI builds the frontend
      image, `kubectl rollout restart deployment/frontend` (no backend
      changes, no migration)
- [ ] 7.2 (User-executed) Spot-check the live site: open `/profile` and
      `/leaderboard`, confirm real data renders and the level control
      persists a change

## 8. Docs

- [ ] 8.1 Update `docs/DEVLOG.md` (session entry) and `docs/ROADMAP.md`
      decision log
- [ ] 8.2 Update root `README.md`'s "Still ahead" list and Core API
      surface table if anything changed
