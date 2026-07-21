## Why

`ai-exercise-svc` (M3 step 2, archived 2026-07-20) calls exactly one LLM
provider (Gemini). If Gemini is down, rate-limited, or returns a
schema-invalid response, the user gets a 502 with no exercises — even
though the user has two other working provider keys (Groq, OpenRouter)
sitting unused. The interview story for this service was always "provider
failover," not "single provider"; this change delivers the piece that was
deliberately deferred when `ai-exercise-svc` was proposed, now that the
single-provider path is proven live.

## What Changes

- `ai-exercise-svc` gains a provider chain: **Gemini → Groq → OpenRouter**
  (in that order — Gemini stays primary since its native structured-output
  mode is the strongest validation guarantee; Groq and OpenRouter are tried
  in turn on any failure).
- A "failure" that triggers fallback to the next provider includes: a
  transport-level error (timeout, connection error, non-2xx from the
  provider), **and** a schema-validation failure of that provider's
  response. Either way, the next provider gets a fresh attempt with the
  same prompt — there's no cross-provider retry-with-backoff, just a single
  attempt per provider in the chain.
- Only if **all** providers fail does the endpoint return the existing 502
  (`GenerationFailedError` → `HTTPException(502)`); the response shape and
  the caching behavior (a failed chain writes nothing to Mongo) are
  unchanged from the existing `exercise-generation` capability.
- Groq and OpenRouter both expose OpenAI-compatible chat completions APIs;
  each will be called via the `openai` Python SDK pointed at that
  provider's base URL, requesting JSON-mode output, then validated through
  the same `GenerationResult` Pydantic model already used for Gemini's
  response — one validation path for all three providers.
- Groq/OpenRouter model IDs are **not** fixed in this proposal (see
  design.md's Open Questions) — provider free-tier model lineups change
  often; the exact IDs are picked and confirmed against each provider's
  current docs during implementation, not guessed here.
- Simulated-failure tests: each provider's call is independently mockable,
  so tests can assert the fallback order (Gemini fails → Groq tried; Groq
  also fails → OpenRouter tried; all three fail → 502) without hitting any
  real provider API.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `exercise-generation`: the existing "Generated exercises are validated
  against a strict JSON schema" requirement (from `ai-exercise-svc`)
  extends from a single provider to a chain — a schema-validation failure
  from one provider is no longer a terminal failure by itself, it triggers
  the next provider in the chain. A new requirement is added: the chain
  falls through to the next provider on any failure (transport or
  validation), and the endpoint only fails once every provider in the
  chain has failed.

## Impact

- **`ai-exercise-svc`**: `app/generation.py` restructured around a list of
  provider callables tried in order; new `app/providers/` module (or
  similar) holding one function per provider; new `openai` dependency
  (Groq and OpenRouter both speak the OpenAI-compatible chat completions
  API); new settings for `GROQ_API_KEY`, `OPENROUTER_API_KEY`, and each
  provider's model ID and base URL.
- **core-api**: no changes — `AiExerciseSvcClient` and `ExerciseService`
  are unaware of what happens inside `ai-exercise-svc`'s `/generate` call;
  this change is entirely internal to that service.
- **Infra**: no new services; `infra/docker-compose.yml`'s existing
  `env_file: ${LLM_KEYS_ENV_PATH}` already supplies all three keys (Groq
  and OpenRouter were already present in the keys file, just unused until
  now).
- **Non-goals / cut line**: no retry-with-backoff within a single provider;
  no configurable/runtime-reorderable chain (order is fixed in code); no
  per-provider circuit breaker or health tracking across requests — each
  `/generate` call independently tries the chain from the top; no cost- or
  latency-based provider selection.
- **Milestone**: M3, step 3 of `docs/ROADMAP.md`'s "Order inside
  milestone."
