## Why

`event-worker` already consumes `user.registered` and `exercise.completed`
to maintain streaks and a leaderboard — real, purpose-built read models,
but purely flat projections (a single current number per user). There's
no derived state that reacts to *history* — crossing a threshold, reaching
a milestone. This was flagged during the post-roadmap planning session as
the one remaining idea with genuine multi-hop event-choreography value
(ingest an event → update streak/completion state → cross-reference that
state against a rule set → derive and persist a new fact), as opposed to
the flat one-event-in-one-row-out shape every other Kafka consumer in this
app has so far. It also gives users a visible reason to come back
(streak/lesson milestones), which the app currently doesn't reward at all
beyond the raw streak number already shown on the profile page.

## What Changes

- `event-worker` evaluates a small, fixed catalog of achievements
  (streak-length and total-completions milestones) every time it processes
  an `exercise.completed` event, immediately after updating streak/stats
  state in the same transaction — no new Kafka topic, no new consumer.
  Newly-crossed thresholds are persisted as unlock records.
- A new internal `GET /users/{userId}/achievements` endpoint on
  event-worker, proxied by core-api as `GET /api/users/me/achievements`,
  returning the full fixed catalog with each entry's locked/unlocked
  status and unlock date.
- A new `AchievementsPage` frontend route showing the catalog as a badge
  grid (unlocked badges shown solid with their unlock date; locked badges
  shown dimmed), linked from the main nav alongside Flashcards/Chat
  practice.

## Capabilities

### New Capabilities
- `achievement-system`: the fixed achievement catalog, its evaluation
  against streak/completion state, the unlock-persistence table, the
  read endpoint, and the frontend badge display.

### Modified Capabilities
(none — `streaks-leaderboard` is read, not changed: achievement
evaluation consumes the streak/stats state that capability already
maintains, but doesn't alter its requirements)

## Impact

- `event-worker/`: new Postgres table `event_worker.user_achievements
  (user_id, achievement_code, unlocked_at)`; a new `total_completions`
  counter column on the existing per-user stats row (currently
  `user_streaks`, already updated on every `exercise.completed` event) so
  completion-count achievements don't need a second event-count query; a
  hardcoded achievement catalog (Go slice of `{code, kind, threshold,
  title, description}`, not a database-configurable table); a new
  `internal/api` handler exposing the read endpoint.
- `core-api/`: `EventWorkerClient` gets a `getAchievements(userId)` method
  (same injected-`RestClient.Builder` pattern as its existing methods); a
  new controller (or a new method added to `UserProfileController`,
  decided during design) exposing `GET /api/users/me/achievements`.
- `frontend/`: new `AchievementsPage.tsx` + route + nav link; reuses the
  existing `.card` styling used by `ProfilePage`/`LeaderboardPage` for the
  badge grid rather than introducing new visual language.
- No new Kafka topics or consumers — achievement evaluation is inline
  logic inside the existing `exercise.completed` handler, not a separate
  hop.

## Non-goals / cut line

- **No new outbound Kafka event** (e.g. `achievement.unlocked`) — nothing
  in the system needs to react to an unlock asynchronously today (no
  notifications service, no email). The frontend discovers new unlocks by
  polling the read endpoint when the achievements page is visited, same
  as the leaderboard/streak pattern already in use. Worth revisiting only
  if a real-time notification need shows up later.
- **No database-configurable achievement catalog / admin UI for it** — a
  fixed, small, hardcoded catalog (a handful of streak and completion
  milestones) is simpler to reason about and verify than a data-driven
  rules engine, and this app has no operational need to add achievements
  without a deploy.
- **No retroactive backfill for existing users** — achievements are
  evaluated going forward from deploy, off future `exercise.completed`
  events. A user with an existing 10-day streak at deploy time won't
  retroactively unlock the "7-day streak" badge until their *next*
  activity event re-evaluates their current state (which will, in
  practice, immediately unlock anything already earned — this is a
  timing nuance, not a missing feature, since the evaluation reads
  current cumulative state, not a delta).
- **No per-achievement progress bar / "3 of 7 days" partial UI** — the
  catalog view shows locked/unlocked only, not incremental progress
  toward the next threshold. A reasonable follow-up, not required for a
  first, finishable slice.
- **No point values, tiers, or a meta-leaderboard of achievement counts**
  — badges are binary unlocked/locked; ranking users by achievement count
  would duplicate the existing streak leaderboard's role without a clear
  reason to.

## Milestone

Post-roadmap, second slice after `chat-rate-limiting` in the
post-roadmap planning session's list (rate limiting, achievement system,
admin UI, extended observability) — chosen next because it's the one with
genuine new architectural texture (multi-hop derived state) versus the
other three, which are all hardening/ops work on existing surfaces.
