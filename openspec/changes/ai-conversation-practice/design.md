## Context

`ai-exercise-svc`'s existing generation path (`app/generation.py`) calls
three providers in a fixed fallback order through `app/providers.py`'s
`call_gemini`/`call_groq`/`call_openrouter` — each takes `(prompt: str,
schema: dict) -> str` and requests **structured/schema-validated JSON
output** from the provider (not free-text chat completions; none of the
three provider calls use a native multi-turn message API). The fallback
loop currently lives inline in `generate_exercises`:

```python
for call_provider in (call_gemini, call_groq, call_openrouter):
    try:
        raw = call_provider(prompt, schema)
        return _parse_result(raw)
    except Exception as exc:
        last_error = exc
        continue
raise GenerationFailedError(str(last_error)) from last_error
```

Note the loop returns as soon as `_parse_result` succeeds for a given
provider — a provider whose raw text calls fine but fails validation still
counts as a failure and falls through, which any shared helper must
preserve.

core-api's `AiExerciseSvcClient` (`core-api/.../client/AiExerciseSvcClient.kt`)
is the only client hitting `ai-exercise-svc` today, built with the
Spring-managed `RestClient.Builder` (not the static factory — see
`docs/ARCHITECTURE.md` §8's note on why that distinction matters for trace
propagation). `User.startingLevel: JlptLevel?` (nullable) already exists
and is user-editable via the `profile-and-progress` slice's `/profile`
page.

## Goals / Non-Goals

**Goals:**
- Reuse the existing structured-output provider machinery for chat,
  rather than introducing a second calling convention (free-text
  completions) alongside it.
- Extract the provider-fallback loop into something both
  `generate_exercises` and the new chat function call, without changing
  either's observable behavior.
- Keep core-api's role a thin, stateless proxy — no new table, no new
  Kafka topic.

**Non-Goals:**
- Multi-turn native chat APIs (OpenAI-style `messages` arrays, Gemini's
  native chat sessions) — every provider call stays a single formatted
  prompt string, matching how `generate_exercises` already formats
  structured input into one prompt.
- Streaming responses — the reply is generated and returned as one JSON
  object, same request/response shape as exercise generation.

## Decisions

- **The chat reply schema mirrors `LessonContent.Dialogue`'s
  `DialogueLine` shape** (`japanese`/`english` fields) plus an optional
  `correction` field, rather than inventing an unrelated shape. Reusing a
  shape the codebase already has consistent conventions for (both here
  and in `GrammarPointCard`'s `ExampleSentence`) is a small but real
  consistency win, and it means the frontend's rendering logic for
  Japanese-plus-translation pairs isn't a first-of-its-kind pattern.
- **The provider-fallback loop is extracted into a `parse` callback
  parameter**, not just moved verbatim:
  ```python
  def call_with_fallback(prompt: str, schema: dict, parse: Callable[[str], T]) -> T:
      last_error: Exception | None = None
      for call_provider in (call_gemini, call_groq, call_openrouter):
          try:
              raw = call_provider(prompt, schema)
              return parse(raw)
          except Exception as exc:
              last_error = exc
              continue
      raise AllProvidersFailedError(str(last_error)) from last_error
  ```
  A bare "try each provider, return the first successful raw string"
  helper would silently change `generate_exercises`'s behavior: today, a
  provider whose raw output fails `_parse_result`'s validation (not just a
  transport error) is treated as a failed attempt and the chain moves on.
  Passing `parse` as a callback keeps that exact behavior for both
  callers, since each supplies its own validation (`_parse_result` for
  exercises, `ChatReply.model_validate_json` for chat) inside the same
  try/except that already catches transport failures.
- **No new `ConversationClient` bean in core-api** — a method is added to
  the existing `AiExerciseSvcClient` instead. Both `/generate` and `/chat`
  target the same downstream service and the same base URL/timeout
  config; the codebase's established convention is one client per
  downstream service, not per endpoint (`EventWorkerClient` already
  exposes both the streak and leaderboard endpoints through one client).
  A second client bean would just duplicate the `RestClient.Builder`/
  timeout wiring for no reason.
- **The JLPT level is derived server-side from `User.startingLevel`**, not
  accepted from the request body. The frontend already has no reason to
  know or send it — the same field is already the single source of truth
  for level-appropriate content on the `/profile` page — and deriving it
  server-side means there's no client-trust question to design around.
  Defaults to N5 when unset, matching `ProfilePage`'s own default.
- **History is capped at 20 messages inside `ai-exercise-svc`**, not
  trusted to whatever the frontend sends. The frontend has no reason to
  trim it itself (simpler to just keep appending to React state for the
  page's lifetime); bounding prompt size is `ai-exercise-svc`'s concern
  since it's the one constructing the prompt, matching where similar
  concerns (schema validation, minimum-exercise-count) already live.

## Risks / Trade-offs

- **[Risk] A long conversation silently loses earlier context past the
  20-message cap** → Mitigation: acceptable for a practice-conversation
  feature (not a research/reference tool); the cap exists specifically to
  bound cost/latency, and 20 messages is 10 full exchanges — long past
  where a short practice conversation naturally winds down.
- **[Risk] Reformatting the entire (bounded) history into one prompt on
  every turn is less token-efficient than a native multi-turn API** →
  Mitigation: accepted deliberately (see Goals/Non-Goals) to avoid a
  second provider-calling convention; conversations are short and capped,
  so the cost difference is small in practice.
- **[Risk] Losing history on refresh may frustrate a user mid-conversation**
  → Mitigation: explicitly accepted in the proposal's non-goals; revisit
  with a `conversations` table if this turns out to matter once the
  feature is actually used.

## Migration Plan

No database migration in any service. Deploy order: `ai-exercise-svc`
first (new `/chat` route is additive, doesn't affect `/generate`), then
core-api (new controller/client method, also additive), then frontend.
Rollback is a plain image rollback per service, same as every prior slice
— nothing here is stateful enough to need a special rollback path.
