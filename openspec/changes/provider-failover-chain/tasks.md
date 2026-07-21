## 1. Provider research

- [x] 1.1 Confirm current, free-tier-eligible Groq model ID (chat
      completions, JSON-mode capable) against Groq's live docs — `openai/gpt-oss-20b`,
      Groq's only strict-JSON-schema-capable model currently
- [x] 1.2 Confirm a current, free/cheap OpenRouter model ID (JSON-mode
      capable) against OpenRouter's live docs — `google/gemma-4-26b-a4b-it:free`,
      chosen deliberately as a different model family from Groq's

## 2. Groq and OpenRouter provider calls

- [x] 2.1 Add `openai` SDK dependency
- [x] 2.2 Add `GROQ_API_KEY`/`groq_model` and
      `OPENROUTER_API_KEY`/`openrouter_model` to `Settings`
- [x] 2.3 Implement `call_groq(prompt, schema) -> str` using
      `openai.OpenAI(api_key=os.environ["GROQ_API_KEY"], base_url=<groq base url>)`
      with strict JSON-schema `response_format`
- [x] 2.4 Implement `call_openrouter(prompt, schema) -> str` the same way,
      pointed at OpenRouter's base URL
- [x] 2.5 ~~Wrap both providers' transport-level failures in a common
      exception type~~ — superseded: `generate_exercises` catches a broad
      `Exception` per provider (see 3.2), so a dedicated exception type
      isn't needed

## 3. Fallback chain

- [x] 3.1 Extract the existing Gemini call into `call_gemini(prompt,
      schema) -> str` (in the new `app/providers.py`, alongside
      `call_groq`/`call_openrouter`), matching the same signature
- [x] 3.2 Rewrite `generate_exercises` to iterate `(call_gemini, call_groq,
      call_openrouter)` in order: on each provider, call it, then
      `GenerationResult.model_validate_json(raw)`; on any exception
      (transport error, `ValidationError`, or `JSONDecodeError`), move to
      the next provider; if the loop exhausts all providers, raise
      `GenerationFailedError` (the existing 502 mapping in `routes.py`
      needed no changes)
- [x] 3.3 Tests (mocking each provider function, no real API calls):
      Gemini succeeds → Groq and OpenRouter are never called; Gemini fails
      (transport) → Groq is called and succeeds → OpenRouter never called;
      Gemini and Groq both fail → OpenRouter is called and succeeds; all
      three fail → `GenerationFailedError` is raised and no provider is
      called twice
- [x] 3.4 Test: a provider returning syntactically valid JSON that fails
      `GenerationResult`'s schema validation (e.g. MCQ with empty options)
      is treated as a failure and falls through to the next provider,
      exactly like a transport error

## 4. Verification

- [x] 4.1 `uv run pytest` and `uv run ruff check .` / `ruff format --check
      .` green — 12/12 tests pass
- [x] 4.2 `docker compose up -d --build`: temporarily point the Gemini
      model at something invalid to force a real failure and confirm a
      real request still returns valid exercises via Groq; restored the
      real Gemini config afterward
- [x] 4.3 Re-measure end-to-end latency for a full fallback (Gemini fails
      → Groq succeeds) against core-api's existing 45s
      `ai-exercise-svc.timeout-seconds` — measured ~62s total (Gemini's
      failure path alone took ~60s), so the timeout was raised to 90s
- [x] 4.4 Update `docs/ROADMAP.md` (M3 step 3 done), `docs/DEVLOG.md`
      (session entry)
