# ai-exercise-svc

**Status:** Not started — Phase 3 (weeks 7–9)

**Stack:** Python + FastAPI, MongoDB

**Responsibility:** Generate language exercises on demand using an LLM provider chain.

- Endpoint: `POST /exercises/generate` — takes user, language, skill level; returns N exercises
- LLM provider abstraction: `GroqProvider` (Llama 3.3, primary) → `OpenRouterProvider` (fallback #1) → `GeminiFlashLiteProvider` (fallback #2), with automatic failover on 429s
- Stores generated content in MongoDB to avoid redundant generation
- Exercise types v1: multiple choice, fill-in-the-blank

Called synchronously by the Core API gateway (user is waiting for the response).
Kafka is not consumed here — side effects (streaks, notifications) are handled by the Event Worker.
