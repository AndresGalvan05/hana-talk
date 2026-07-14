# ai-exercise-svc

**Status:** Not started — Milestone 3

**Stack:** Python + FastAPI, MongoDB

**Responsibility:** Generate Japanese exercises on demand using an LLM provider
chain, graded against JLPT levels (N5–N1).

- Endpoint: `POST /exercises/generate` — takes lesson content + JLPT level,
  returns N exercises (multiple choice, fill-in-the-blank).
- LLM provider abstraction with automatic failover on 429s/5xx. Provider
  choice is finalized at Milestone 3 kickoff (candidates: Groq, OpenRouter,
  Gemini Flash-Lite free tiers); the interface is provider-agnostic.
- Strict JSON-schema validation of LLM output, with retry on invalid responses.
- Caches generated exercises in MongoDB keyed by lesson + level to avoid
  redundant generation.

Called synchronously by the Core API gateway (the user is waiting for the
response). Grading and progress recording stay in the gateway — this service
only generates content. Kafka is not consumed here; side effects (streaks,
leaderboard) are handled by the Event Worker.
