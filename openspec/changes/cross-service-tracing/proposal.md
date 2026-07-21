## Why

M5's interview story leads with cross-service OTel tracing, but today
tracing is effectively dead code: core-api has had
`micrometer-tracing-bridge-otel` and `opentelemetry-exporter-otlp` on the
classpath since M1, exporting to `http://localhost:4318` — which inside a
container means "this container," so every export attempt has failed with
`Connection refused` since day one (a known, previously-accepted log-noise
item in `docs/DEVLOG.md`). No trace has ever actually been produced, let
alone propagated across the Kafka hop into `event-worker` or the HTTP hops
into `ai-exercise-svc`/`event-worker`. This change makes tracing real: a
working local collector, and trace context that actually crosses every
service boundary this app has.

## What Changes

- **A local trace collector**: `jaegertracing/all-in-one` (native OTLP
  ingestion, UI at `:16686`) added to `docker-compose.yml`. All three JVM/
  Python/Go services point their OTLP exporter at it. This is deliberately
  separate from the Grafana Cloud dashboards item in `docs/ROADMAP.md`'s M5
  list — Grafana Cloud needs an external account/API key (the same kind of
  blocker LLM keys were for M3) and is scoped as its own follow-up change;
  this change is unblocked and gives real local trace verification today.
- **core-api (producer + HTTP client)**:
  - `spring.kafka.template.observation-enabled: true` — the one property
    that was missing for `EventPublisher`'s `KafkaTemplate.send()` calls to
    inject a W3C `traceparent` header into `user.registered`/
    `exercise.completed` records (verified against Spring Kafka's official
    sample: this is genuinely all that's needed on the producer side, no
    new dependency).
  - **BREAKING for internal wiring, not for any public contract**:
    `AiExerciseSvcClient` and `EventWorkerClient` currently build their
    `RestClient` via the static `RestClient.builder()` factory method —
    which Spring Boot's own docs confirm bypasses all auto-configuration,
    including the `ObservationRestClientCustomizer` that injects trace
    headers. Both are changed to inject Spring Boot's auto-configured
    `RestClient.Builder` bean instead (still customized with the existing
    per-service timeout), so calls to `ai-exercise-svc` and `event-worker`
    are actually traced. This was found and verified via Spring Boot's own
    documentation while researching this change, not assumed.
- **`ai-exercise-svc` (incoming HTTP)**: `opentelemetry-sdk`,
  `opentelemetry-exporter-otlp-proto-http`, and
  `opentelemetry-instrumentation-fastapi` added; FastAPI auto-instrumented
  so an incoming request's `traceparent` header (now actually sent, per the
  core-api fix above) is extracted and continues the trace for the
  `/generate` span.
- **`event-worker` (incoming HTTP + incoming Kafka)**: `go.opentelemetry.io/
  otel` SDK + OTLP HTTP exporter; `otelhttp` wraps the internal read API so
  incoming calls from core-api continue the trace; the Kafka consumer
  manually extracts `traceparent` from each message's headers (there is no
  off-the-shelf Kafka-go OTel instrumentation as standardized as the HTTP
  ecosystem's) and starts a span as a child of the producer's trace before
  processing.
- **Frontend**: out of scope — the trace originates at core-api, not the
  browser; no browser-side OTel SDK is added.

## Capabilities

### New Capabilities
- `distributed-tracing`: a request that touches Kafka and/or a downstream
  HTTP call produces a single trace spanning every service it passes
  through, exported to an OTLP collector.

### Modified Capabilities
(none — this instruments existing request/event flows, it doesn't change
any endpoint's behavior or contract)

## Impact

- **core-api**: `application.yml` (Kafka observation property, OTLP
  endpoint default), `AiExerciseSvcClient.kt`/`EventWorkerClient.kt`
  (inject `RestClient.Builder` instead of the static factory) — no new
  Gradle dependencies.
- **`ai-exercise-svc`**: new OTel dependencies, a small SDK-setup addition
  to `app/main.py`.
- **`event-worker`**: new OTel Go dependencies, SDK setup in `main.go`,
  `otelhttp`-wrapped API server, manual header extraction in the consumer.
- **infra**: `docker-compose.yml` gains a `jaeger` service; all four
  services (core-api, ai-exercise-svc, event-worker — frontend excluded)
  get an `OTEL_EXPORTER_OTLP_ENDPOINT` pointed at it.
- **Non-goals / cut line**: no Grafana Cloud wiring (separate M5 change,
  blocked on an external account); no tracing of outbound LLM-provider
  calls from `ai-exercise-svc` (Gemini/Groq/OpenRouter) — those stay
  un-instrumented sub-operations for this slice; no metrics/logs
  correlation beyond what Micrometer already does for core-api; no
  frontend/browser tracing; no load testing or chaos engineering (per
  M5's existing cut line).
- **Milestone**: M5, first of several slices (mirrors the M3 split
  pattern) — Grafana Cloud dashboards, the admin role, and the
  architecture/demo-script docs are separate follow-up changes.
