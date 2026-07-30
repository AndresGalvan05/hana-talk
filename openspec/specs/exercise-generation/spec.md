# exercise-generation Specification

## Purpose
TBD - created by archiving change ai-exercise-svc. Update Purpose after archive.
## Requirements
### Requirement: A lesson with no exercises triggers generation on request
The system SHALL generate exercises for a lesson when it has no existing
`Exercise` rows, by calling `ai-exercise-svc` with the lesson's content and
JLPT level, and SHALL persist the validated result as ordinary `Exercise`
rows before serving the request.

#### Scenario: First request for a lesson with no exercises
- **WHEN** an authenticated user requests exercises for a lesson that has
  zero `Exercise` rows
- **THEN** core-api calls `ai-exercise-svc`, persists the returned exercises
  as `Exercise` rows, and returns them to the caller in the same shape as
  seeded exercises (no answer field)

#### Scenario: Lesson already has exercises
- **WHEN** an authenticated user requests exercises for a lesson that
  already has `Exercise` rows (seeded or previously generated)
- **THEN** core-api serves them directly and does not call
  `ai-exercise-svc`

### Requirement: Generated exercises are validated against a strict JSON schema
`ai-exercise-svc` SHALL validate each exercise in a provider's response
individually against a schema matching the existing exercise shape, and
SHALL keep only the schema-conforming exercises from that response rather
than discarding the whole batch over one malformed item. Shape rules per
type: MCQ requires non-empty options and a correct answer among them;
fill-in-blank and translation require a correct answer and no options;
sentence-ordering requires non-empty options (the shuffled word tokens)
whose token set, split from the correct answer by spaces, exactly matches
the options' token set. The surviving exercises from a provider's response
must still meet the minimum batch size and type-variety floor (at least 4
exercises, including at least one MCQ and one fill-in-blank); if they
don't, that provider's attempt counts as a failure and the next provider in
the chain is tried.

#### Scenario: Provider returns a fully schema-conforming response
- **WHEN** a provider returns a response where every exercise matches the
  expected shape for its type
- **THEN** `ai-exercise-svc` returns all of them to core-api as validated
  exercises, without attempting any further provider

#### Scenario: A provider returns one malformed exercise among otherwise-valid ones
- **WHEN** a provider's response contains one exercise that fails schema
  validation (e.g. a sentence-ordering correct answer using different words
  than its options) alongside other exercises that pass validation, and the
  surviving exercises still meet the minimum-variety bar
- **THEN** `ai-exercise-svc` drops only the malformed exercise and returns
  the valid ones, without attempting any further provider

#### Scenario: Every provider in the chain fails
- **WHEN** every provider's response fails outright (transport error) or
  leaves too few valid exercises to meet the minimum-variety bar, for the
  same generation request
- **THEN** `ai-exercise-svc` responds with an error status and core-api
  does not persist any `Exercise` rows for that request

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

### Requirement: Generated exercises are cached per lesson to avoid repeat generation
`ai-exercise-svc` SHALL cache a successful generation result in MongoDB keyed
by lesson id, and SHALL serve subsequent requests for the same lesson id from
the cache without calling the LLM provider again.

#### Scenario: Second generation request for the same lesson
- **WHEN** `ai-exercise-svc` receives a generation request for a lesson id it
  has already successfully generated exercises for
- **THEN** it returns the cached result without calling the LLM provider

#### Scenario: Cache miss
- **WHEN** `ai-exercise-svc` receives a generation request for a lesson id
  with no cache entry
- **THEN** it calls the LLM provider, validates the response, writes it to
  the cache, and returns it
