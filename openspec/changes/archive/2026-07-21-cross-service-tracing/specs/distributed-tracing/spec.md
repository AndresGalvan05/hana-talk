## ADDED Requirements

### Requirement: A published Kafka event carries trace context that a consumer can continue
core-api SHALL inject the active trace's context into the headers of every
`user.registered` and `exercise.completed` Kafka record it publishes, and
`event-worker` SHALL extract that context when consuming a record and
continue the same trace for its processing of that message.

#### Scenario: A lesson completion is traced end to end
- **WHEN** a user completes a lesson, triggering a Kafka publish of
  `exercise.completed`
- **THEN** `event-worker`'s processing of that message appears as part of
  the same trace as the originating HTTP request, not a separate,
  unrelated trace

### Requirement: An HTTP call from core-api to a downstream service carries trace context
core-api SHALL propagate the active trace's context in the HTTP headers of
every call it makes to `ai-exercise-svc` and `event-worker`, and each
downstream service SHALL extract that context and continue the same trace
for its handling of that request.

#### Scenario: An exercise-generation request is traced end to end
- **WHEN** core-api calls `ai-exercise-svc`'s `/generate` endpoint as part
  of handling a user's request
- **THEN** `ai-exercise-svc`'s span for that request is part of the same
  trace as the originating request into core-api

#### Scenario: A leaderboard/streak request is traced end to end
- **WHEN** core-api calls `event-worker`'s internal leaderboard or streak
  endpoint
- **THEN** `event-worker`'s span for that request is part of the same
  trace as the originating request into core-api

### Requirement: All traced spans export to a local collector
Every service that produces spans (core-api, `ai-exercise-svc`,
`event-worker`) SHALL export them to a reachable OTLP collector in the
local docker-compose environment, rather than to an unreachable default
endpoint.

#### Scenario: Local stack export succeeds
- **WHEN** the local docker-compose stack is running
- **THEN** spans produced by core-api, `ai-exercise-svc`, and
  `event-worker` are visible in the local collector's UI, correlated by
  trace id
