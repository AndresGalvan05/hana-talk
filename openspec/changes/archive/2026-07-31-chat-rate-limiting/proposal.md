## Why

`POST /api/conversation/reply` is live, public, authenticated-but-
otherwise-unbounded, and each call costs a real request against free-tier
LLM keys (Gemini/Groq/OpenRouter). Nothing today stops a single user from
sending requests as fast as the client can fire them. This was explicitly
flagged as a gap in `ai-conversation-practice`'s non-goals ("no rate-
limiting or abuse prevention beyond what already exists elsewhere in the
app (i.e. none)") — this change closes it for the one endpoint that
actually needs it.

## What Changes

- A per-user, in-memory, fixed-window rate limiter (10 requests/minute)
  applied to `POST /api/conversation/reply`, returning `429 Too Many
  Requests` once exceeded.
- The frontend chat page shows a specific "you're sending messages too
  fast" message on a 429, distinct from its existing generic-failure
  message, and still preserves the user's typed message for retry
  (reusing the same restore-on-failure behavior already built).

## Capabilities

### New Capabilities
- `chat-rate-limiting`: the per-user request cap on the chat endpoint and
  its frontend error handling.

### Modified Capabilities
(none)

## Impact

- `core-api/`: new `RateLimiter` component (in-memory, generic
  key/limit/window — not chat-specific in itself); `ConversationController`
  checks it before calling `AiExerciseSvcClient`; a new `Clock` bean for
  testability.
- `frontend/`: `ChatPage.tsx` distinguishes a 429 response from other
  failures.
- No database migration, no new infrastructure (no Redis) — in-memory
  state, consistent with this app's existing single-replica, free-tier-
  constrained deployment.

## Non-goals / cut line

- **Exercise generation is explicitly excluded.** Unlike chat,
  `GET /api/lessons/{id}/exercises` only ever calls `ai-exercise-svc` once
  per lesson, ever — `ExerciseService.listByLesson` short-circuits
  permanently once any `Exercise` rows exist for that lesson. With five
  lessons in the app today, the entire lifetime abuse surface is five real
  LLM calls; rate-limiting an already-self-limiting, permanently-cached
  path would solve a problem that doesn't exist here.
- No distributed/shared rate-limit state (Redis, a shared counter service)
  — in-memory per-pod state is accepted because core-api runs a single
  replica in production today; if that ever changes, each replica would
  enforce its own independent limit, effectively multiplying the real cap
  by replica count. Worth revisiting only if that becomes true.
- No `Retry-After` response header — the frontend already knows the
  window is fixed at one minute and can say so in its own copy without
  the server needing to echo timing back.
- No generic `@RateLimited` annotation/aspect framework — one endpoint
  needs this today; a declarative framework for N endpoints is
  unjustified complexity until there's a second one that needs it.
- No per-IP or unauthenticated-request limiting — every endpoint in scope
  already requires JWT authentication, so the limiter keys purely on user
  identity.

## Milestone

Post-roadmap, post-deepening-interactivity-plan work — the first slice
proposed after the original five-slice content/interactivity plan (plus
`profile-and-progress`) was fully shipped and archived. A production-
hardening concern rather than a content/feature slice.
