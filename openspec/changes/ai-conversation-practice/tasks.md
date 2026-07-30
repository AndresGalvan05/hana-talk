## 1. ai-exercise-svc: shared provider-fallback helper

- [ ] 1.1 New `app/llm_fallback.py`: `call_with_fallback(prompt, schema,
      parse)` extracted from `generate_exercises`'s loop, taking a `parse`
      callback so a provider whose raw output fails validation (not just
      transport errors) still falls through to the next provider — exact
      behavior preserved, not just moved. New `AllProvidersFailedError`.
- [ ] 1.2 Update `generate_exercises` in `app/generation.py` to call
      `call_with_fallback(prompt, schema, _parse_result)`, catching
      `AllProvidersFailedError` and re-raising as the existing
      `GenerationFailedError` (so `routes.py`'s except clause and the
      public error contract are unchanged)
- [ ] 1.3 `tests/test_generation.py`'s existing fallback-order and
      malformed-response tests still pass unchanged (they test observable
      behavior, not the internal loop) — confirms the extraction didn't
      change behavior

## 2. ai-exercise-svc: chat generation

- [ ] 2.1 Add `ChatMessage` (`speaker: Literal["user", "tutor"]`,
      `japanese: str`), `ChatRequest` (`jlpt_level: str`,
      `history: list[ChatMessage]`, `message: str`), and `ChatReply`
      (`japanese: str`, `english: str`, `correction: str | None`) to
      `app/schemas.py`, with validators requiring non-empty
      `japanese`/`english`
- [ ] 2.2 New `app/chat.py`: `get_chat_reply(jlpt_level, history, message)
      -> ChatReply`, mirroring `generation.py`'s structure — a prompt
      template instructing the tutor persona, JLPT-level-appropriate
      vocabulary, and the correction behavior (correct only real
      Japanese-language mistakes, leave `correction` null otherwise);
      trims `history` to the most recent 20 messages before formatting the
      prompt; calls `call_with_fallback(prompt, schema,
      ChatReply.model_validate_json)`; wraps `AllProvidersFailedError` in
      a new `ChatFailedError`
- [ ] 2.3 New `POST /chat` route in `app/routes.py`, `response_model=
      ChatReply`, catching `ChatFailedError` as a 502 (same pattern as
      `/generate`'s `GenerationFailedError` handling) — no caching (each
      turn is unique, unlike per-lesson exercise generation)
- [ ] 2.4 New `tests/test_chat.py`: provider fallback order (mirroring
      `test_generation.py`'s three tests), malformed-reply-falls-through,
      all-providers-fail raises `ChatFailedError`, history longer than 20
      messages is trimmed before prompt construction (assert on the
      prompt text passed to the mocked provider call)
- [ ] 2.5 `ruff check`/`ruff format`/`pytest` green

## 3. core-api: conversation endpoint

- [ ] 3.1 Add `ChatMessageDto` (`speaker: String`,
      `@JsonProperty("japanese") val japanese: String`), `ChatRequestDto`
      (`@JsonProperty("jlpt_level") val jlptLevel: String`, `history:
      List<ChatMessageDto>`, `message: String`), `ChatReplyDto`
      (`japanese: String`, `english: String`, `correction: String?`) to
      `AiExerciseSvcDtos.kt`
- [ ] 3.2 Add `getChatReply(jlptLevel: String, history:
      List<ChatMessageDto>, message: String): ChatReplyDto` to the
      existing `AiExerciseSvcClient` (not a new client bean — see
      `design.md`), posting to `/chat`, same try/catch-to-502 pattern as
      `generateExercises`
- [ ] 3.3 New `ConversationReplyRequest`/`ConversationReplyResponse` DTOs
      (frontend-facing, no `jlpt_level` field — the frontend never
      supplies it) and a `ConversationController` with
      `POST /api/conversation/reply`: looks up the authenticated user via
      `@AuthenticationPrincipal`, resolves `user.startingLevel ?:
      JlptLevel.N5`, calls `aiExerciseSvcClient.getChatReply(...)`
- [ ] 3.4 New `ConversationControllerTest` (`@WebMvcTest`): 401 without
      auth, derives N5 for a user with no stored level, derives the
      stored level when set, propagates a client failure as the same
      error status `AiExerciseSvcClient` already throws
- [ ] 3.5 `ktlintCheck test` green

## 4. Frontend

- [ ] 4.1 Add `ChatMessage`/`ChatReply` types to `frontend/src/api/types.ts`
- [ ] 4.2 New `frontend/src/pages/ChatPage.tsx`: message list rendered
      from local `useState` (no fetch on mount — a conversation starts
      empty), text input + send button, `api.post<ChatReply>
      ('/api/conversation/reply', { history, message })` per send;
      appends the user's message immediately (optimistic) and the tutor's
      reply once it resolves; disables input while a reply is pending;
      renders a correction in a visually distinct element from the
      conversational reply when present
- [ ] 4.3 Error handling: a failed request shows a retryable error without
      discarding the user's typed message (per `conversation-practice-ui`
      spec) — keep the failed message in the input rather than clearing it
      on failure
- [ ] 4.4 New `/chat` route in `App.tsx` (inside the authenticated
      `Layout` route tree), nav link in `Layout.tsx`
- [ ] 4.5 New CSS in `frontend/src/index.css` for the chat transcript,
      message bubbles (user vs. tutor), and the correction element —
      reusing `.card`/existing color variables where it fits
- [ ] 4.6 `oxlint` and `tsc -b && vite build` both green

## 5. Local verification

- [ ] 5.1 `docker compose up -d --build`; open `/chat`, send a message
      with a deliberate grammar mistake, confirm a correction renders
      distinctly from the reply; send a correct message, confirm no
      correction renders
- [ ] 5.2 Send enough messages to exceed the 20-message history cap;
      confirm the conversation keeps working (older context silently
      drops, no error)
- [ ] 5.3 Force an `ai-exercise-svc` outage (stop the container) mid
      conversation; confirm the frontend shows a retryable error and the
      typed message isn't lost; confirm the conversation resumes once the
      service is back
- [ ] 5.4 Confirm a user with no `startingLevel` set gets a reply (N5
      default) and a user who set a level via `/profile` gets a
      level-appropriate one (spot-check via prompt/response content, not
      just that a reply arrives)
- [ ] 5.5 `ktlintCheck test` (core-api) and `ruff`/`pytest`
      (ai-exercise-svc) both green

## 6. Production rollout

- [ ] 6.1 Deploy — merge to `main`, CI builds `ai-exercise-svc`, core-api,
      and frontend images; `kubectl rollout restart` all three (no
      migration needed — no new database state in any service)
- [ ] 6.2 Spot-check the live site: hold a short conversation on `/chat`,
      confirm a correction appears for a deliberate mistake

## 7. Docs

- [ ] 7.1 Update `docs/DEVLOG.md` (session entry) and `docs/ROADMAP.md`
      decision log
- [ ] 7.2 Update root `README.md`'s "Still ahead" list (move AI
      conversation practice to "Shipped so far") and the Core API surface
      table (new `POST /api/conversation/reply` row)
