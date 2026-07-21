## Context

`ai-exercise-svc/app/generation.py` currently hardcodes one call path:
build a prompt, call `genai.Client().interactions.create(...)` with a
`response_format` schema derived from `GenerationResult.model_json_schema()`,
then `GenerationResult.model_validate_json(interaction.output_text)`,
catching `ValidationError`/`json.JSONDecodeError` into a single
`GenerationFailedError` that `routes.py` turns into a 502. `Settings`
(`app/config.py`) only knows `gemini_model`; `GROQ_API_KEY` and
`OPENROUTER_API_KEY` already reach the container's environment via the same
`env_file: ${LLM_KEYS_ENV_PATH}` wiring as `GEMINI_API_KEY`, but nothing
reads them yet.

## Goals / Non-Goals

**Goals:**
- Any single provider failing (down, rate-limited, times out, or returns a
  response that fails schema validation) falls through to the next
  provider in a fixed chain, transparent to core-api and to the end user.
- The existing schema-validation guarantee is preserved regardless of which
  provider ultimately produces the result — `GenerationResult` stays the
  one validation boundary, applied uniformly to every provider's raw
  output.
- The three provider call paths are independently unit-testable without
  hitting any real API, so the fallback order itself is verified by tests,
  not by hoping a real provider happens to be down during a test run.

**Non-Goals:**
- No backoff/retry *within* a single provider — one attempt per provider,
  then move on.
- No dynamic/runtime-configurable chain ordering, no per-provider health
  tracking across requests, no cost-based routing.
- No change to the Mongo cache or the `/generate` request/response
  contract — a failed chain still writes nothing to Mongo, exactly as a
  failed single-provider call does today.
- No change to core-api — this is entirely internal to `ai-exercise-svc`.

## Decisions

**Chain order: Gemini → Groq → OpenRouter.** Gemini stays primary — its
native structured-output mode (SDK-level parsing straight into a Pydantic
model) is the strongest per-provider guarantee. Groq and OpenRouter are
tried in that order as fallbacks; both are OpenAI-compatible APIs, so a
single implementation shape covers both (see below), and Groq is generally
faster/cheaper than OpenRouter's routed models, which is why it comes
second rather than third.

**Groq and OpenRouter via the `openai` SDK's `base_url` override.** Both
providers implement an OpenAI-compatible `chat.completions` endpoint. Rather
than writing two bespoke HTTP clients, both use
`openai.OpenAI(api_key=..., base_url=...)` with JSON-mode
(`response_format={"type": "json_object"}`) and the same
`GenerationResult` JSON schema described in the prompt (OpenAI-compatible
JSON mode does not enforce a schema server-side the way Gemini's
structured output does — it only guarantees *valid JSON*, not
*schema-conforming JSON* — so the existing Pydantic validation step is what
actually enforces the shape for these two providers, not the provider
itself). This is a deliberate trade-off: Gemini's response is more likely
to validate on the first try; Groq/OpenRouter responses are more likely to
need the fallback path, which is exactly why Gemini is primary.

**One provider function signature, tried in a loop.** Each provider is a
function `(prompt: str) -> str` (raw JSON text) or one that raises on
transport failure. `generate_exercises` becomes:
```python
PROVIDERS: list[Callable[[str], str]] = [_call_gemini, _call_groq, _call_openrouter]

def generate_exercises(content, jlpt_level) -> GenerationResult:
    prompt = _build_prompt(content, jlpt_level)
    last_error: Exception | None = None
    for call_provider in PROVIDERS:
        try:
            raw = call_provider(prompt)
            return GenerationResult.model_validate_json(raw)
        except (TransportError, ValidationError, json.JSONDecodeError) as exc:
            last_error = exc
            continue
    raise GenerationFailedError(str(last_error))
```
This keeps `routes.py` and the `GenerationFailedError` → 502 mapping
completely unchanged; only `generation.py` grows internally.

**Model IDs deferred to implementation.** Groq and OpenRouter's current
free-tier model lineups are not fixed in this design — they change more
often than Gemini's, and guessing a specific ID here risks the design
referencing a model that's deprecated by the time this is implemented.
Task 1 of `tasks.md` is to look up and confirm current, free-tier-eligible
model IDs for both providers against their live docs at implementation
time.

## Risks / Trade-offs

- [Groq/OpenRouter JSON mode doesn't enforce the schema server-side, only
  valid JSON] → acceptable: this is exactly the scenario the fallback chain
  exists to absorb — a schema-invalid response from either just falls
  through, same as a transport failure.
- [Total worst-case latency triples if Gemini and Groq both fail before
  OpenRouter succeeds] → acceptable for a portfolio-scale, low-traffic
  demo; core-api's existing 45s timeout (tuned in the `ai-exercise-svc`
  change after measuring a real Gemini call) has headroom, but should be
  re-measured once this change lands in case three sequential provider
  calls approach it — flagged as a verification step, not a blocker.
- [Three provider SDKs/API shapes to keep working] → mitigated by Groq and
  OpenRouter sharing one `openai`-SDK-based implementation; only Gemini
  needs its own code path.

## Open Questions

- Exact Groq and OpenRouter model IDs — resolved during implementation
  (task 1), not fixed here.
