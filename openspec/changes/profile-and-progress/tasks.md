## 1. API client & types

- [x] 1.1 Add `Streak` and `LeaderboardEntry` types to
      `frontend/src/api/types.ts` (`{ userId, currentStreak, lastActiveDate }`
      and `{ userId, username, currentStreak }`, matching core-api's
      `StreakResponse`/`LeaderboardEntryDto`)
- [x] 1.2 ~~Add wrapper functions to `client.ts`~~ — every existing page
      calls `api.get`/`api.post`/`api.patch` directly inline (confirmed:
      no named wrapper functions exist anywhere in `client.ts` today, not
      even for courses/lessons); `ProfilePage`/`LeaderboardPage` follow
      that same convention instead of introducing a new one

## 2. Profile page

- [x] 2.1 New `frontend/src/pages/ProfilePage.tsx`: fetch profile + streak
      on mount (`useEffect`/`useState`, matching `CoursesPage`'s pattern),
      show username, current JLPT level, and streak count
- [x] 2.2 Level-change control: a `<select>` of the five `JlptLevel`
      values plus a Save button; on save, call `setLevel` and update local
      state from the response, no page reload
- [x] 2.3 Handle the zero-streak case explicitly (render "0", not a blank
      or error state)
- [x] 2.4 Link to the leaderboard page from the profile page

## 3. Leaderboard page

- [x] 3.1 New `frontend/src/pages/LeaderboardPage.tsx`: fetch the ranked
      list on mount, render as a table/list ordered as returned (already
      highest-streak-first from the API)
- [x] 3.2 Distinguish the signed-in user's own row by comparing each
      entry's `username` against the current user's username, sourced
      from `useAuth()` (matches `Layout.tsx`'s existing pattern) rather
      than reaching into `tokenStore` directly
- [x] 3.3 Empty-state rendering when the leaderboard has zero entries

## 4. Navigation

- [x] 4.1 Add a profile link to the site header in
      `frontend/src/components/Layout.tsx`, next to the existing
      username/logout controls
- [x] 4.2 Add `/profile` and `/leaderboard` routes in `frontend/src/App.tsx`,
      inside the existing authenticated `Layout` route tree

## 5. Styling

- [x] 5.1 Add profile/leaderboard rules to `frontend/src/index.css`
      (reusing `.card` where it fits), including a distinct style for the
      leaderboard's "this is you" row

## 6. Local verification

- [x] 6.1 `docker compose up -d --build`, `npm run dev`; registered a
      fresh user (`profiletest`), opened `/profile` — username/level(N5
      default)/streak(0 days) all rendered correctly; changed level to N3,
      reloaded, confirmed it stuck
- [x] 6.2 Completed a lesson to fire `exercise.completed` — confirmed via
      Kafka console consumer that core-api published it correctly, but
      this environment's `event-worker` consumer group got stuck
      rebalancing and never processed it (a pre-existing local Kafka/
      consumer-group issue, unrelated to this change's code — production's
      event-worker has been consuming fine in every prior session).
      Worked around it by seeding a streak row directly in
      `event_worker.user_streaks` (local dev DB only) to verify the
      actual thing this task cares about: `/profile` correctly showed the
      streak (3 days) and `/leaderboard` correctly ranked and highlighted
      that user's own row (accent border + filled rank badge)
- [x] 6.3 `npm run lint` (oxlint) and `npm run build` (`tsc -b && vite
      build`) both green

## 7. Production rollout

- [ ] 7.1 (User-executed) Deploy — merge to `main`, CI builds the frontend
      image, `kubectl rollout restart deployment/frontend` (no backend
      changes, no migration)
- [ ] 7.2 (User-executed) Spot-check the live site: open `/profile` and
      `/leaderboard`, confirm real data renders and the level control
      persists a change

## 8. Docs

- [x] 8.1 Update `docs/DEVLOG.md` (session entry) and `docs/ROADMAP.md`
      decision log
- [x] 8.2 Update root `README.md`'s "Still ahead" list (Core API surface
      table unchanged — no new/modified endpoints, this was pure frontend
      work against existing ones)
