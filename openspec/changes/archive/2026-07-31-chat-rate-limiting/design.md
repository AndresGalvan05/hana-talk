## Context

`ConversationController.reply()` (`core-api/.../api/ConversationController.kt`)
already resolves the authenticated user via `UserRepository.findByEmail`
before calling `aiExerciseSvcClient.getChatReply(...)` — that's the exact
point a rate-limit check needs to sit, since it already has both the
user's identity and is the single call site that reaches the LLM chain.
Every existing error path in this controller (and the rest of core-api)
throws `ResponseStatusException(status, message)` and lets Spring's
default handling produce the response — `AiExerciseSvcClient` itself
already does this for `BAD_GATEWAY`. There is no existing precedent in
this codebase for a custom `Filter`/`HandlerInterceptor` beyond
`JwtAuthFilter` itself, and no Redis or other shared-state store in the
stack (deliberately cut back at M4).

`frontend/src/api/client.ts`'s `ApiError` already captures `status` on
every failed request; `ChatPage.tsx`'s `send()` catch block currently
treats every failure identically.

## Goals / Non-Goals

**Goals:**
- Stop chat abuse without adding a new moving part to the request
  pipeline (no filter, no interceptor) — just a checked call at the one
  place that already needs it.
- Keep the counting logic itself generic and reusable (key + limit +
  window as parameters), even though only one call site uses it today.

**Non-Goals:**
- See proposal.md's cut line — no exercise-generation limiting, no
  distributed state, no `Retry-After` header, no annotation framework.

## Decisions

- **Inline check in `ConversationController.reply()`, not a `Filter`.** A
  `OncePerRequestFilter` (mirroring `JwtAuthFilter`) was considered — it
  would be reusable across paths without touching controller code again,
  and is the more "textbook" way to implement a cross-cutting concern.
  Rejected for now: it's real added complexity (filter-chain ordering
  relative to `JwtAuthFilter` so `SecurityContextHolder` is already
  populated, handling requests where authentication never succeeded,
  hand-writing the 429 response body instead of getting Spring's default
  exception handling for free) for a single call site. `ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
  ...)` gets the correct HTTP semantics with the same one-line pattern
  every other error in this codebase already uses. Revisit as a filter if
  a second LLM-backed, unbounded, per-request endpoint is ever added.
- **`RateLimiter` is a generic, reusable `@Component`**, not a
  chat-specific class — `tryAcquire(key: String, maxRequests: Int, window:
  Duration): Boolean`, called as
  `rateLimiter.tryAcquire("chat:${user.id}", 10, Duration.ofMinutes(1))`.
  The counting logic costs nothing extra to make generic (the key/limit/
  window are already the natural parameters), so it's not premature
  abstraction — it just means a second call site later is a one-line
  addition, not a copy-pasted class.
- **Fixed-window counter, not a sliding-window log or token bucket.** A
  `ConcurrentHashMap<String, Window>` where `Window` holds a mutable
  count and window-start `Instant`, with the per-key check synchronized
  on that key's own `Window` object (not a global lock, so unrelated
  users' requests never contend). A fixed window is slightly less
  precise than a sliding log (it allows up to 2x the limit across a
  window boundary in the worst case) but is simpler to implement and
  reason about — acceptable for "stop obvious spam," not a billing-
  grade guarantee.
- **A `Clock` bean (`Clock.systemUTC()`, added to `HanaTalkApplication.kt`)
  is injected into `RateLimiter`** instead of calling `Instant.now()`
  directly, purely so tests can use `Clock.fixed(...)` (or a small mutable
  test double) to deterministically test window rollover without
  `Thread.sleep`.
- **Frontend distinguishes 429 via `err instanceof ApiError && err.status
  === 429`** in `ChatPage.tsx`'s existing catch block — `ApiError` already
  carries `status` on every request, so no new API surface is needed,
  just a branch on an already-available value.

## Risks / Trade-offs

- **[Risk] In-memory state means the limit is per-pod, not truly global**
  → Accepted per proposal's non-goals; core-api runs one replica in
  production today.
- **[Risk] Fixed-window counting allows a burst of up to ~2x the stated
  limit right at a window boundary** → Accepted; the goal is stopping
  obvious abuse (hundreds of rapid requests), not precise quota
  enforcement.
- **[Risk] The `windows` map grows one entry per distinct user who has
  ever sent a chat message, and entries are never evicted** → Not a
  practical concern at this app's user scale (a portfolio demo, not a
  production SaaS with unbounded signups); worth a TTL/eviction policy
  only if that stops being true.

## Migration Plan

None — no database change, no new dependency, no infrastructure. Deploys
through the existing core-api CI → GHCR → `kubectl rollout restart`
path, same as every prior core-api change. Frontend ships in the same
rollout since the 429-specific message has nothing to react to without
the backend change.
