# 2-Minute Demo Script

Run against the **local docker-compose stack**, not the live site —
production doesn't have `ai-exercise-svc`/`event-worker` deployed (see
`docs/ARCHITECTURE.md`), so the local stack is the only place the full
polyglot story (LLM generation + failover, Kafka-driven streaks, and
cross-service tracing) can be shown end to end today.

**Before the interview starts**, get the stack up and warm so the
demo itself isn't waiting on Docker:

```bash
cd infra && docker compose up -d --build
```

Have two browser tabs ready: `http://localhost:5173` (the app) and
`http://localhost:16686` (Jaeger UI). Register one throwaway user ahead of
time so login is instant during the demo.

| Time | Action | Say |
|---|---|---|
| 0:00–0:15 | Show the running app at `localhost:5173`, logged in. | "HanaTalk is a Japanese learning app — the interview story is the backend, not the UI: a Kotlin API gateway, a Python LLM service with provider failover, and a Go Kafka consumer, all polyglot on purpose." |
| 0:15–0:30 | Click into the seeded N5 course, open a lesson. | "Content is seeded via Flyway — five lessons, JLPT N5." |
| 0:30–0:55 | Scroll to "Practice exercises" on a lesson that has no exercises yet. Watch the loading message; if it upgrades to "still generating," call that out live. | "This lesson has never been requested before, so core-api is calling out to the Python service right now — it tries Gemini first, falls back to Groq then OpenRouter on any failure, validates the response against a strict schema, and caches it so this only ever happens once per lesson." |
| 0:55–1:15 | Submit the correct answer to one exercise. Point out the completion banner appearing without a page reload. | "A correct answer reuses the exact same completion path as the manual 'mark complete' button — one completion mechanism, two ways to trigger it." |
| 1:15–1:30 | Switch to a terminal, `curl localhost:8080/api/users/me/streak` (or the leaderboard) with the demo user's token. | "That completion also published a Kafka event — a Go service consumes it independently and computed this streak. Nothing on the request path waited for it." |
| 1:30–1:50 | Switch to the Jaeger tab, search for a recent trace, open one that spans the lesson-completion request. | "This is one trace spanning the HTTP request into core-api, the Kafka publish, and the Go service's consumption of that same message — genuine cross-service tracing, not per-service log correlation." |
| 1:50–2:00 | Back in the terminal, `curl -X POST localhost:8080/api/courses -d '{...}'` once as the demo user (403), then again after a one-line SQL promotion to admin (201). | "Course mutation is admin-gated — this same account just got denied, then allowed, after one `UPDATE` on the role column. No re-login needed; the role is re-checked from the database on every request." |

Total: ~2 minutes. If time allows, mention the Grafana Cloud dashboard
(`infra/grafana/core-api-overview.json`) as the observability story —
request rate, error rate, JVM memory, and Kafka publish rate, exported
directly from core-api with no in-cluster Prometheus/Grafana. (Verified
against the real Grafana Cloud account from this local stack; rolling it
onto the production cluster is a documented, separate step.)

## Fallbacks

- If the lesson you pick already has cached exercises from a prior run,
  the "watch it generate live" beat is lost — pick a lesson id you haven't
  touched yet, or `docker compose down -v` beforehand for a clean slate
  (re-seeds on next `up`).
- If a provider is having a bad day and the whole chain fails, that's
  itself a legitimate thing to show: a clean `502`, not a hang or a crash
  — the interview story survives a live failure better than most demos do.
