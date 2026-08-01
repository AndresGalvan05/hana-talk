## 1. Rate limiter

- [ ] 1.1 Add `@Bean fun clock(): Clock = Clock.systemUTC()` to
      `HanaTalkApplication.kt`
- [ ] 1.2 New `core-api/src/main/kotlin/online/hanatalk/security/RateLimiter.kt`:
      `@Component` taking an injected `Clock`; `tryAcquire(key: String,
      maxRequests: Int, window: Duration): Boolean` using a
      `ConcurrentHashMap<String, Window>` (mutable `count`/`windowStartedAt`
      per key), synchronized per-key (not globally) on check-and-increment,
      resetting the window when elapsed time exceeds `window`

## 2. Wiring into the chat endpoint

- [ ] 2.1 `ConversationController`: inject `RateLimiter`; at the top of
      `reply()`, after resolving the user, call
      `rateLimiter.tryAcquire("chat:${user.id}", 10, Duration.ofMinutes(1))`
      and throw `ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
      "Too many messages -- please wait a moment and try again.")` if it
      returns `false`, before calling `aiExerciseSvcClient.getChatReply`

## 3. Frontend

- [ ] 3.1 `ChatPage.tsx`: import `ApiError` from `../api/client`; in the
      `send()` catch block, set a specific message ("You're sending
      messages too fast — wait a moment and try again.") when `err
      instanceof ApiError && err.status === 429`, otherwise keep the
      existing generic message; `input` restoration on failure stays
      exactly as it already is for both cases
- [ ] 3.2 `oxlint` and `tsc -b && vite build` both green

## 4. Tests

- [ ] 4.1 `RateLimiterTest`: allows requests up to the limit within a
      window; denies the next one; allows again once the window has
      elapsed (using an injected fixed/mutable `Clock` test double, no
      `Thread.sleep`); two different keys are tracked independently
- [ ] 4.2 `ConversationControllerTest`: add a `RateLimiter` mock (stub
      `tryAcquire` to return `true` by default so existing tests are
      unaffected); new test asserting a `429` response and that
      `aiExerciseSvcClient.getChatReply` is never called when
      `tryAcquire` returns `false`
- [ ] 4.3 `ktlintCheck test` green

## 5. Local verification

- [ ] 5.1 `docker compose up -d --build`, `npm run dev`; send 10 chat
      messages in quick succession, confirm the 11th shows the specific
      "too fast" message and the typed message is preserved in the input
- [ ] 5.2 Wait for the window to elapse (or restart the pod / use a
      fresh window in a follow-up test) and confirm sending again
      succeeds
- [ ] 5.3 Confirm a normal, low-frequency conversation is completely
      unaffected (well under the limit)

## 6. Production rollout

- [ ] 6.1 Deploy — merge to `main`, CI builds core-api and frontend
      images, `kubectl rollout restart` both (no migration)
- [ ] 6.2 Spot-check the live site: confirm normal chat still works;
      optionally confirm the 429 path if convenient to trigger safely

## 7. Docs

- [ ] 7.1 Update `docs/DEVLOG.md` (session entry) and `docs/ROADMAP.md`
      decision log
- [ ] 7.2 Update root `README.md`'s Core API surface table if the
      `POST /api/conversation/reply` row needs a note about the new limit
