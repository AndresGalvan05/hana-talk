## 1. Provider research

- [ ] 1.1 Confirm current, free-tier-eligible Groq model ID (chat
      completions, JSON-mode capable) against Groq's live docs
- [ ] 1.2 Confirm a current, free/cheap OpenRouter model ID (JSON-mode
      capable) against OpenRouter's live docs

## 2. Groq and OpenRouter provider calls

- [ ] 2.1 Add `openai` SDK dependency
- [ ] 2.2 Add `GROQ_API_KEY`/`groq_model` and
      `OPENROUTER_API_KEY`/`openrouter_model` to `Settings`
- [ ] 2.3 Implement `_call_groq(prompt) -> str` using
      `openai.OpenAI(api_key=settings_groq_key, base_url=<groq base url>)`
      with JSON-mode `response_format`
- [ ] 2.4 Implement `_call_openrouter(prompt) -> str` the same way, pointed
      at OpenRouter's base URL
- [ ] 2.5 Wrap both providers' transport-level failures (timeouts,
      connection errors, non-2xx) in a common exception type so
      `generate_exercises` can catch one type across all providers

## 3. Fallback chain

- [ ] 3.1 Extract the existing Gemini call into a `_call_gemini(prompt) ->
      str` function returning raw JSON text (currently
      `interaction.output_text`), matching the same signature as the Groq
      and OpenRouter functions
- [ ] 3.2 Rewrite `generate_exercises` to iterate `[gemini, groq,
      openrouter]` in order: on each provider, call it, then
      `GenerationResult.model_validate_json(raw)`; on transport error OR
      `ValidationError`/`JSONDecodeError`, move to the next provider; if
      the loop exhausts all providers, raise `GenerationFailedError` (the
      existing 502 mapping in `routes.py` needs no changes)
- [ ] 3.3 Tests (mocking each provider function, no real API calls):
      Gemini succeeds → Groq and OpenRouter are never called; Gemini fails
      (transport) → Groq is called and succeeds → OpenRouter never called;
      Gemini and Groq both fail → OpenRouter is called and succeeds; all
      three fail → `GenerationFailedError` is raised and no provider is
      called twice
- [ ] 3.4 Test: a provider returning syntactically valid JSON that fails
      `GenerationResult`'s schema validation (e.g. MCQ with empty options)
      is treated as a failure and falls through to the next provider,
      exactly like a transport error

## 4. Verification

- [ ] 4.1 `uv run pytest` and `uv run ruff check .` / `ruff format --check
      .` green
- [ ] 4.2 `docker compose up -d --build`: temporarily point the Gemini
      model/key at something invalid (or otherwise force a Gemini failure)
      and confirm a real request still returns valid exercises via Groq;
      restore the real Gemini config afterward
- [ ] 4.3 Re-measure end-to-end latency for a full fallback (Gemini fails
      → Groq succeeds) against core-api's existing 45s
      `ai-exercise-svc.timeout-seconds`; adjust the timeout if the margin
      is thin
- [ ] 4.4 Update `docs/ROADMAP.md` (M3 step 3 done), `docs/DEVLOG.md`
      (session entry)
