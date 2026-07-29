## MODIFIED Requirements

### Requirement: Generated exercises are validated against a strict JSON schema
`ai-exercise-svc` SHALL validate every LLM provider response against a
schema matching the existing exercise shape before returning it, and SHALL
reject non-conforming responses rather than passing them through partially.
Shape rules per type: MCQ requires non-empty options and a correct answer
among them; fill-in-blank and translation require a correct answer and no
options; sentence-ordering requires non-empty options (the shuffled word
tokens) whose token set, split from the correct answer by spaces, exactly
matches the options' token set. A schema-validation failure from one
provider SHALL NOT immediately fail the request — it triggers the next
provider in the chain (see the provider fallback chain requirement below).

#### Scenario: Provider returns a schema-conforming response
- **WHEN** a provider returns a response matching the expected shape for
  the requested exercise type
- **THEN** `ai-exercise-svc` returns it to core-api as validated exercises,
  without attempting any further provider

#### Scenario: A provider returns a malformed response
- **WHEN** a provider returns a response that fails schema validation
  (missing fields, wrong types, empty options for MCQ, a sentence-ordering
  correct answer using different words than its options, etc.)
- **THEN** `ai-exercise-svc` does not persist or return that response, and
  instead attempts the next provider in the chain

#### Scenario: Every provider in the chain fails
- **WHEN** all providers in the chain fail (via transport error or schema
  validation) for the same generation request
- **THEN** `ai-exercise-svc` responds with an error status and core-api
  does not persist any `Exercise` rows for that request
