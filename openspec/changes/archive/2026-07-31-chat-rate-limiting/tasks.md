## 1. Rate limiter

- [x] 1.1 Added `@Bean fun clock(): Clock = Clock.systemUTC()` to
      `HanaTalkApplication.kt`
- [x] 1.2 New `core-api/src/main/kotlin/online/hanatalk/security/RateLimiter.kt`:
      `@Component` taking an injected `Clock`; `tryAcquire(key: String,
      maxRequests: Int, window: Duration): Boolean` using a
      `ConcurrentHashMap<String, Window>` (mutable `count`/`windowStartedAt`
      per key), synchronized per-key (not globally) on check-and-increment,
      resetting the window when elapsed time exceeds `window`

## 2. Wiring into the chat endpoint

- [x] 2.1 `ConversationController`: injected `RateLimiter`; after
      resolving the user, calls
      `rateLimiter.tryAcquire("chat:${user.id}", 10, Duration.ofMinutes(1))`
      and throws `ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
      "Too many messages -- please wait a moment and try again.")` if it
      returns `false`, before calling `aiExerciseSvcClient.getChatReply`

## 3. Frontend

- [x] 3.1 `ChatPage.tsx`: imported `ApiError` from `../api/client`; the
      `send()` catch block sets a specific message ("You're sending
      messages too fast — wait a moment and try again.") when `err
      instanceof ApiError && err.status === 429`, otherwise keeps the
      existing generic message; `input` restoration on failure stays
      exactly as it already is for both cases
- [x] 3.2 `oxlint` and `tsc -b && vite build` both green

## 4. Tests

- [x] 4.1 `RateLimiterTest` (4 tests): allows requests up to the limit
      within a window; denies the next one; allows again once the window
      has elapsed (using a small `MutableClock` test double implementing
      `java.time.Clock`, no `Thread.sleep`); two different keys are
      tracked independently
- [x] 4.2 `ConversationControllerTest`: added a `RateLimiter`
      `@MockitoBean` (stubbed `tryAcquire` to return `true` in each
      existing success-path test, since an unstubbed mock returns
      `false` for a non-null `Boolean` return type); new test asserting
      a `429` response and that `aiExerciseSvcClient.getChatReply` is
      never called when `tryAcquire` returns `false`
- [x] 4.3 `ktlintCheck test` green (10 tests across both files)

## 5. Local verification

- [x] 5.1 `docker compose up -d --build`, `npm run dev`; send 10 chat
      messages in quick succession, confirm the 11th shows the specific
      "too fast" message and the typed message is preserved in the input.
      Verified with 15 concurrent curl requests (sequential requests are
      too slow individually — real LLM latency of 6-30s/request means
      the 60s window resets before 10 can land within it — so the limit
      was confirmed via concurrency instead): exactly 10x200 + 5x429.
      Then confirmed in the actual browser UI: with the limit exhausted,
      sending a message shows "You're sending messages too fast — wait a
      moment and try again." and preserves the typed text in the input.
- [x] 5.2 Wait for the window to elapse (or restart the pod / use a
      fresh window in a follow-up test) and confirm sending again
      succeeds. Verified: after the 60s window elapsed, the same user
      sent a message via the UI and got a normal reply.
- [x] 5.3 Confirm a normal, low-frequency conversation is completely
      unaffected (well under the limit). Verified throughout regular
      manual chat use this session, and by the passing
      `ConversationControllerTest` success-path tests.

## 6. Production rollout

- [x] 6.1 Deploy — merge to `main`, CI builds core-api and frontend
      images, `kubectl rollout restart` both (no migration). Both
      deployments rolled out successfully.
- [x] 6.2 Spot-check the live site: confirm normal chat still works;
      optionally confirm the 429 path if convenient to trigger safely.
      Verified against production: a normal reply succeeded (200), then
      11 requests in the same window returned 10x200 + 1x429, matching
      the configured 10/min limit.

## 7. Docs

- [x] 7.1 Update `docs/DEVLOG.md` (session entry) and `docs/ROADMAP.md`
      decision log
- [x] 7.2 Update root `README.md`'s Core API surface table if the
      `POST /api/conversation/reply` row needs a note about the new limit
