# frontend

**Status:** In progress — Milestone 1

**Stack:** React + TypeScript, Vite

**Responsibility:** Japanese-learning UI — the only client of the Core API
gateway.

- Register / login (JWT), protected routes.
- Browse courses by JLPT level (N5–N1), read lessons, mark them complete.
- Per-course progress view.
- Later milestones: AI-generated exercise answering (M3), streaks and
  leaderboard (M4).

Talks exclusively to the Core API. No direct calls to ai-exercise-svc or
event-worker.

## Development

```bash
npm install
npm run dev        # dev server on http://localhost:5173, API at http://localhost:8080
```

Configure the API base URL with `VITE_API_URL` (defaults to
`http://localhost:8080`).
