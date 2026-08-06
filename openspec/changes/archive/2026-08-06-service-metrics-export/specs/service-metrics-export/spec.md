## Purpose

Gives operational visibility into the two services that previously
exported traces but no metrics at all — `ai-exercise-svc` (LLM
provider call health) and `event-worker` (Kafka consumer health) —
reusing their existing OTLP connection configuration.

## ADDED Requirements

### Requirement: LLM provider calls are recorded as metrics
`ai-exercise-svc` SHALL record the duration and outcome (success or
failure) of every LLM provider call attempt made through its
provider-fallback path, labeled by provider name.

#### Scenario: A successful provider call is recorded
- **WHEN** an LLM provider call succeeds and its response is parsed
  successfully
- **THEN** a metric recording that call's duration and a success
  outcome, labeled with that provider's name, is recorded

#### Scenario: A failed provider call is recorded before falling back
- **WHEN** an LLM provider call fails or its response fails to parse
- **THEN** a metric recording that call's duration and a failure
  outcome, labeled with that provider's name, is recorded before the
  next provider is attempted

### Requirement: Kafka message processing is recorded as metrics
`event-worker` SHALL record the duration and outcome (success or
failure) of every Kafka message it processes, labeled by topic.

#### Scenario: A successfully processed message is recorded
- **WHEN** `event-worker` successfully processes a Kafka message
- **THEN** a metric recording that message's processing duration and a
  success outcome, labeled with the message's topic, is recorded

#### Scenario: A failed message processing attempt is recorded
- **WHEN** `event-worker` fails to process a Kafka message
- **THEN** a metric recording that message's processing duration and a
  failure outcome, labeled with the message's topic, is recorded

### Requirement: Metrics export is disabled by default and reuses existing OTLP configuration
Both `ai-exercise-svc` and `event-worker` SHALL export their recorded
metrics via OTLP only when explicitly enabled, using the same OTLP
endpoint and authentication configuration already used for tracing, and
SHALL NOT attempt metrics export when not explicitly enabled.

#### Scenario: Metrics export is off by default
- **WHEN** a service starts without metrics export explicitly enabled
- **THEN** no metrics export is attempted, and the service starts
  normally

#### Scenario: Metrics export is enabled
- **WHEN** a service starts with metrics export explicitly enabled
- **THEN** recorded metrics are exported via OTLP using the same
  endpoint and authentication already configured for that service's
  trace export
