# 2-Minute Demo Script

Run against the **live production site**, https://hanatalk.online —
`ai-exercise-svc`, `event-worker`, and MongoDB were deployed to the k3s
cluster on 2026-07-27 (see `docs/DEVLOG.md`), so the full polyglot story
(LLM generation + failover, Kafka-driven streaks, and cross-service
tracing) is now demoable against the real thing, not a local stand-in.

**Before the interview starts:**

- Register one throwaway user at https://hanatalk.online ahead of time so
  login is instant during the demo. This is a real account in the
  production database — harmless, but it's not something a `compose down -v`
  wipes afterward. **Don't register it more than ~45 minutes before you
  start** — JWTs expire after 1 hour (`JwtService.kt`), and re-logging in
  mid-demo is an easy but avoidable interruption.
- Note which of the five seeded N5 lessons (fixture IDs in `CLAUDE.md`,
  course `0b4f9a12-1111-...`) you'll open for the "watch it generate live"
  beat, and **don't open it yourself beforehand** — the first-ever request
  for a lesson is the only one that triggers a real LLM call; every repeat
  after that is served from MongoDB's cache in milliseconds, and there's no
  way to reset a lesson's cache short of asking an operator to delete its
  `Exercise` rows. Rehearse the rest of the flow against a *different*
  already-cached lesson so you don't burn your only live-generation moment
  (and don't waste free-tier LLM quota) in practice.
- Have two browser tabs ready: https://hanatalk.online (the app) and your
  Grafana Cloud **Explore** view, pre-filtered to `{resource.service.name="core-api"}`
  so a trace search is one click away.

| Time | Action | Say |
|---|---|---|
| 0:00–0:15 | Show the live app at hanatalk.online, logged in. | "HanaTalk is a Japanese learning app — the interview story is the backend, not the UI: a Kotlin API gateway, a Python LLM service with provider failover, and a Go Kafka consumer, all polyglot on purpose, all running on a single free-tier VM." |
| 0:15–0:30 | Click into the seeded N5 course, open a lesson. | "Content is seeded via Flyway — five lessons, JLPT N5." |
| 0:30–0:55 | Scroll to "Practice exercises" on the lesson you held back. Watch the loading message; if it upgrades to "still generating," call that out live. | "This lesson has never been requested before, so core-api is calling out to the Python service right now — it tries Gemini first, falls back to Groq then OpenRouter on any failure, validates the response against a strict schema, and caches it so this only ever happens once per lesson, ever." |
| 0:55–1:15 | Submit the correct answer to one exercise. Point out the completion banner appearing without a page reload. | "A correct answer reuses the exact same completion path as the manual 'mark complete' button — one completion mechanism, two ways to trigger it." |
| 1:15–1:30 | Switch to a terminal, `curl https://hanatalk.online/api/users/me/streak` (or the leaderboard) with the demo user's token. | "That completion also published a Kafka event — a Go service consumes it independently and computed this streak. Nothing on the request path waited for it." |
| 1:30–1:50 | Switch to the Grafana Cloud tab, find a recent trace for the lesson-completion request. | "This is one trace spanning the HTTP request into core-api, the Kafka publish, and the Go service's consumption of that same message, exported straight to Grafana Cloud — genuine cross-service tracing, not per-service log correlation, and no in-cluster collector eating RAM on a free-tier node." |
| 1:50–2:00 | Back in the terminal, `curl -X POST https://hanatalk.online/api/courses -d '{...}'` once as the demo user (403), then again after a one-line SQL promotion to admin (201). | "Course mutation is admin-gated — this same account just got denied, then allowed, after one `UPDATE` on the role column. No re-login needed; the role is re-checked from the database on every request." |

Total: ~2 minutes. If time allows, pull up the Grafana Cloud dashboard
(`infra/grafana/core-api-overview.json`) as the observability story —
request rate, error rate, JVM memory, and Kafka publish rate, now showing
real production traffic rather than a local-stack stand-in, exported
directly from core-api with no in-cluster Prometheus/Grafana.

**After the interview:** delete the demo course created in the 1:50–2:00
step (`DELETE /api/courses/{id}` as the admin account) — production data
should reflect real content, not interview scratch work.

## Fallbacks

- If every seeded lesson already has cached exercises (a prior demo, or
  someone else poking at the site), the "watch it generate live" beat is
  lost — there's no local `docker compose down -v` equivalent on
  production. Fall back to narrating the generation/caching/failover design
  from `docs/ARCHITECTURE.md` instead of showing it live, or ask an
  operator to clear one lesson's `Exercise` rows ahead of time.
- If a provider is having a bad day and the whole chain fails, that's
  itself a legitimate thing to show: a clean `502`, not a hang or a crash
  — the interview story survives a live failure better than most demos do.
- If you'd rather not risk a live LLM call failing mid-interview at all,
  it's fine to fall back to the docker-compose flow this script used before
  2026-07-27 — the tracing tab is then Jaeger (`localhost:16686`) instead
  of Grafana Cloud, everything else is identical.
