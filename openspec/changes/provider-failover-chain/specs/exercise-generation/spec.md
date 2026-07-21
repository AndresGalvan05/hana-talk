## MODIFIED Requirements

### Requirement: Generated exercises are validated against a strict JSON schema
`ai-exercise-svc` SHALL validate every LLM provider response against a
schema matching the existing exercise shape (MCQ requires non-empty options
and a correct answer among them; fill-in-blank requires a correct answer and
no options) before returning it, and SHALL reject non-conforming responses
rather than passing them through partially. A schema-validation failure from
one provider SHALL NOT immediately fail the request — it triggers the next
provider in the chain (see the provider fallback chain requirement below).

#### Scenario: Provider returns a schema-conforming response
- **WHEN** a provider returns a response matching the expected shape for
  the requested exercise type
- **THEN** `ai-exercise-svc` returns it to core-api as validated exercises,
  without attempting any further provider

#### Scenario: A provider returns a malformed response
- **WHEN** a provider returns a response that fails schema validation
  (missing fields, wrong types, empty options for MCQ, etc.)
- **THEN** `ai-exercise-svc` does not persist or return that response, and
  instead attempts the next provider in the chain

#### Scenario: Every provider in the chain fails
- **WHEN** all providers in the chain fail (via transport error or schema
  validation) for the same generation request
- **THEN** `ai-exercise-svc` responds with an error status and core-api
  does not persist any `Exercise` rows for that request

## ADDED Requirements

### Requirement: A provider failure falls through to the next provider in the chain
`ai-exercise-svc` SHALL attempt providers in a fixed order — Gemini, then
Groq, then OpenRouter — for each generation request, moving to the next
provider immediately when the current one fails (transport error, timeout,
or schema-validation failure), without retrying the failed provider.

#### Scenario: Primary provider fails, fallback succeeds
- **WHEN** Gemini fails (transport error or invalid response) for a
  generation request
- **THEN** `ai-exercise-svc` attempts Groq next with the same prompt,
  without retrying Gemini

#### Scenario: First two providers fail, last one succeeds
- **WHEN** both Gemini and Groq fail for the same generation request
- **THEN** `ai-exercise-svc` attempts OpenRouter, and if it succeeds,
  returns its validated result as if it were the only provider called

#### Scenario: A successful provider is not followed by further attempts
- **WHEN** a provider earlier in the chain (e.g. Gemini) succeeds
- **THEN** `ai-exercise-svc` does not call any later provider in the chain
  for that request
