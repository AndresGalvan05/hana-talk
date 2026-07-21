## ADDED Requirements

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
`ai-exercise-svc` SHALL validate every LLM provider response against a
schema matching the existing exercise shape (MCQ requires non-empty options
and a correct answer among them; fill-in-blank requires a correct answer and
no options) before returning it, and SHALL reject non-conforming responses
rather than passing them through partially.

#### Scenario: Provider returns a schema-conforming response
- **WHEN** the LLM provider returns a response matching the expected shape
  for the requested exercise type
- **THEN** `ai-exercise-svc` returns it to core-api as validated exercises

#### Scenario: Provider returns a malformed response
- **WHEN** the LLM provider returns a response that fails schema validation
  (missing fields, wrong types, empty options for MCQ, etc.)
- **THEN** `ai-exercise-svc` responds with an error status and core-api does
  not persist any `Exercise` rows for that request

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
