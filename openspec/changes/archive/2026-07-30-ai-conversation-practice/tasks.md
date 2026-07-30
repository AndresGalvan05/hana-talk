## 1. ai-exercise-svc: shared provider-fallback helper

- [x] 1.1 New `app/llm_fallback.py`: `call_with_fallback(prompt, schema,
      parse)` extracted from `generate_exercises`'s loop, taking a `parse`
      callback so a provider whose raw output fails validation (not just
      transport errors) still falls through to the next provider — exact
      behavior preserved, not just moved. New `AllProvidersFailedError`.
- [x] 1.2 Update `generate_exercises` in `app/generation.py` to call
      `call_with_fallback(prompt, schema, _parse_result)`, catching
      `AllProvidersFailedError` and re-raising as the existing
      `GenerationFailedError` (so `routes.py`'s except clause and the
      public error contract are unchanged)
- [x] 1.3 `tests/test_generation.py`'s tests test observable behavior, not
      the internal loop, and all 18 still pass — confirming the
      extraction didn't change behavior. Their `patch("app.generation
      .call_gemini", ...)` targets did need updating to
      `app.llm_fallback.call_gemini` etc., since that's where the names
      actually live now — an expected mechanical follow-on of moving the
      loop, not a behavior change

## 2. ai-exercise-svc: chat generation

- [x] 2.1 Add `ChatMessage` (`speaker: Literal["user", "tutor"]`,
      `japanese: str`), `ChatRequest` (`jlpt_level: str`,
      `history: list[ChatMessage]`, `message: str`), and `ChatReply`
      (`japanese: str`, `english: str`, `correction: str | None`) to
      `app/schemas.py`, with validators requiring non-empty
      `japanese`/`english`
- [x] 2.2 New `app/chat.py`: `get_chat_reply(jlpt_level, history, message)
      -> ChatReply`, mirroring `generation.py`'s structure — a prompt
      template instructing the tutor persona, JLPT-level-appropriate
      vocabulary, and the correction behavior (correct only real
      Japanese-language mistakes, leave `correction` null otherwise);
      trims `history` to the most recent 20 messages before formatting the
      prompt; calls `call_with_fallback(prompt, schema,
      ChatReply.model_validate_json)`; wraps `AllProvidersFailedError` in
      a new `ChatFailedError`
- [x] 2.3 New `POST /chat` route in `app/routes.py`, `response_model=
      ChatReply`, catching `ChatFailedError` as a 502 (same pattern as
      `/generate`'s `GenerationFailedError` handling) — no caching (each
      turn is unique, unlike per-lesson exercise generation)
- [x] 2.4 New `tests/test_chat.py`: provider fallback order (mirroring
      `test_generation.py`'s tests), malformed-reply-falls-through,
      all-providers-fail raises `ChatFailedError`, history longer than 20
      messages is trimmed before prompt construction (asserted on the
      prompt text passed to the mocked provider call). Also added
      `test_chat_success_returns_reply`/`test_chat_failure_returns_502`
      to `tests/test_routes.py`, matching `/generate`'s route-level
      coverage
- [x] 2.5 `ruff check`/`ruff format`/`pytest` green (25 tests)

## 3. core-api: conversation endpoint

- [x] 3.1 Added `ChatMessageDto` (`speaker: String`, `japanese: String`),
      `ChatRequestDto` (`@JsonProperty("jlpt_level") val jlptLevel:
      String`, `history: List<ChatMessageDto>`, `message: String`),
      `ChatReplyDto` (`japanese: String`, `english: String`, `correction:
      String?`) to `AiExerciseSvcDtos.kt`
- [x] 3.2 Added `getChatReply(jlptLevel: String, history:
      List<ChatMessageDto>, message: String): ChatReplyDto` to the
      existing `AiExerciseSvcClient` (not a new client bean — see
      `design.md`), posting to `/chat`, same try/catch-to-502 pattern as
      `generateExercises`
- [x] 3.3 New `ConversationReplyRequest` DTO (`history:
      List<ChatMessageDto>, message: String` — reuses `ChatMessageDto`
      directly rather than a duplicate type, since the shape is
      identical) and a `ConversationController` with
      `POST /api/conversation/reply`: looks up the authenticated user via
      `@AuthenticationPrincipal`, resolves `user.startingLevel ?:
      JlptLevel.N5`, calls `aiExerciseSvcClient.getChatReply(...)`,
      returning `ChatReplyDto` directly (no separate response DTO needed
      — the client's response shape is already exactly what the frontend
      needs). File named `ConversationReplyRequest.kt` per ktlint's
      single-class-per-file naming rule, not `ConversationDtos.kt`
- [x] 3.4 New `ConversationControllerTest` (`@WebMvcTest`, 5 tests): 401
      without auth, derives N5 for a user with no stored level, derives
      the stored level when set, propagates a client failure as the same
      error status `AiExerciseSvcClient` already throws, 404s if the
      authenticated principal has no matching `User` row
- [x] 3.5 `ktlintCheck test` green

## 4. Frontend

- [x] 4.1 Added `ChatMessage`/`ChatReply` types to `frontend/src/api/types.ts`
- [x] 4.2 New `frontend/src/pages/ChatPage.tsx`: message list from local
      `useState` (no fetch on mount), text input + send button,
      `api.post<ChatReply>('/api/conversation/reply', { history,
      message })` per send. A `pending: string | null` state (rather than
      optimistically appending to the transcript then rolling back on
      failure) holds the in-flight message and renders it as a distinct
      "sending…" bubble; both the user turn and the tutor's reply are
      only committed to the transcript together, on success — simpler
      than add-then-possibly-remove. Correction renders in a visually
      distinct dashed-border element separate from the conversational
      reply
- [x] 4.3 Error handling: on failure, `input` is restored to the message
      text that failed (it was cleared optimistically on send) so the
      user can retry without retyping; `pending` clears so the input/
      button re-enable
- [x] 4.4 New `/chat` route in `App.tsx` (inside the authenticated
      `Layout` route tree), "Chat practice" nav link in `Layout.tsx`
- [x] 4.5 New CSS in `frontend/src/index.css` for the chat transcript,
      message bubbles (user vs. tutor, right/left aligned), the pending
      bubble, and the correction element
- [x] 4.6 `oxlint` and `tsc -b && vite build` both green

## 5. Local verification

- [x] 5.1 `docker compose up -d --build`; opened `/chat`, sent
      「わたしは がくせい あります。」(a deliberate です/あります mistake) —
      correction rendered distinctly ("Instead of 'ga arimasu', use
      'desu'...") with the reply continuing the conversation naturally;
      sent a correct follow-up, confirmed no correction rendered
- [x] 5.2 Covered by `test_history_longer_than_the_cap_is_trimmed...` at
      the unit level (asserts on the actual prompt text sent to the
      provider, which is a more precise check than clicking through 20+
      real messages) — not repeated manually in the browser
- [x] 5.3 Stopped `infra-ai-exercise-svc-1` mid-conversation, sent a
      message: error shown, typed message restored to the input,
      transcript intact, input re-enabled; restarted the container,
      clicked Send again with no retyping needed — reply arrived
      continuing the conversation with full prior context
- [x] 5.4 Found and fixed a real gap during this check: the initial
      prompt said "at or below {jlpt_level}", which for N1 (the top of
      the scale) doesn't push toward harder language at all, and the
      model defaulted to simple, all-hiragana replies regardless of
      level. First fix (bidirectional framing) was insufficient; a
      second, concrete-example-driven version of the prompt (see
      `app/chat.py`) fixed it. Verified properly after also catching that
      the first "save N1" attempt silently didn't persist (confirmed via
      `SELECT starting_level FROM users` — a UI/automation click issue,
      not a product bug) — redid it, confirmed N1 in the database, then
      confirmed the reply used genuinely adult-level vocabulary (複雑,
      円安, 物価高騰) versus N5's simple hiragana-heavy replies for the same
      question
- [x] 5.5 `ktlintCheck test` (core-api, 5 new tests) and `ruff`/`pytest`
      (ai-exercise-svc, 25 tests) both green

## 6. Production rollout

- [x] 6.1 Deployed — merged to `main`, CI built `ai-exercise-svc`,
      core-api, and frontend images; `kubectl rollout restart` all three
      (SSH tunnel had dropped again since the last session — re-
      established per the known gotcha before deploying;
      `ai-exercise-svc`'s `rollout status` timed out but `get pods`
      confirmed it actually succeeded, matching the documented gotcha)
- [x] 6.2 Spot-checked the live site: registered a fresh account, sent
      「わたしは がくせい あります。」 on `/chat`, got a real reply with an
      accurate correction ("use です instead of あります...") rendered in
      the distinct correction box

## 7. Docs

- [x] 7.1 Updated `docs/DEVLOG.md` (session entry) and `docs/ROADMAP.md`
      decision log
- [x] 7.2 Updated root `README.md`'s "Still ahead" list (moved AI
      conversation practice to "Shipped so far") and the Core API surface
      table (new `POST /api/conversation/reply` row)
