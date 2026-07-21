## Context

Three services already ship OTel-adjacent dependencies but none of it
works end-to-end today:
- core-api has `micrometer-tracing-bridge-otel` +
  `opentelemetry-exporter-otlp` since M1, with
  `management.tracing.sampling.probability` and
  `management.otlp.tracing.endpoint` already configurable in
  `application.yml` (defaulting to `http://localhost:4318/v1/traces`,
  which inside a container resolves to the container itself — this has
  been failing silently since M1, documented as accepted noise in
  `docs/DEVLOG.md`).
- `ai-exercise-svc` and `event-worker` have zero tracing today.
- `EventPublisher.kt` publishes two Kafka topics with no trace context in
  the record headers; nothing downstream could pick up a trace even if
  the collector worked.
- `AiExerciseSvcClient`/`EventWorkerClient` (from M3/M4) both build their
  `RestClient` via `RestClient.builder()` — confirmed via Spring Boot's
  own docs (`io/rest-client.adoc`) that this bypasses all
  auto-configuration: "you can fall back to the original API and use
  `RestClient.create()`. In that case, no auto-configuration or
  RestClientCustomizer is applied." The fix is injecting the
  Spring-managed `RestClient.Builder` bean instead.

## Goals / Non-Goals

**Goals:**
- A single trace spans: an HTTP request into core-api → a Kafka publish
  → `event-worker`'s consumption of that message → and, independently, an
  HTTP request into core-api → its call to `ai-exercise-svc` or
  `event-worker`'s internal API.
- A real, running local collector (Jaeger) so this is verifiable by
  looking at an actual trace, not by trusting configuration.
- Minimal, surgical changes — this is instrumentation, not a rewrite.

**Non-Goals:**
- Grafana Cloud (separate, external-account-blocked M5 slice).
- Tracing `ai-exercise-svc`'s outbound LLM provider calls.
- Any change to request/response contracts, business logic, or the
  frontend.

## Decisions

**Jaeger `all-in-one`, not a bare OTel Collector.** Jaeger's all-in-one
image has ingested OTLP natively since 1.35 and ships a query UI
(`:16686`) in the same container — a single compose service gives both
ingestion and a way to actually look at the result, which a bare
Collector alone would not. This is purely a local verification tool for
this change; it is not a production observability decision (that's the
Grafana Cloud slice).

**`spring.kafka.template.observation-enabled: true` is the entire
producer-side fix.** Verified directly against Spring Kafka's own
`sample-08`, which demonstrates exactly this property (plus
`spring.kafka.listener.observation-enabled`, not needed here since
core-api has no `@KafkaListener`) producing a shared trace ID across a
producer and consumer. No new dependency, no code change to
`EventPublisher` — the existing autoconfigured `KafkaTemplate` bean picks
up the property directly.

**Inject `RestClient.Builder`, don't call `RestClient.builder()`.**
Rejected alternative: manually add a `ClientHttpRequestInterceptor` to
inject `traceparent` by hand. Rejected because Spring Boot already ships
exactly this behavior (`ObservationRestClientCustomizer`) for free — the
bug was never "missing tracing support," it was "two components
accidentally opted out of it" by using the static factory instead of the
managed bean. Both clients keep their existing per-service timeout
customization, just applied to the injected builder instead of a
freshly-constructed one.

**Go and Python each use their ecosystem's standard OTel HTTP
instrumentation, but Kafka needs manual header extraction in Go.** HTTP
context propagation is a solved, standardized problem in both ecosystems
(`otelhttp` for Go, `opentelemetry-instrumentation-fastapi` for Python) —
using the off-the-shelf middleware is strictly better than hand-rolling
extraction for every handler. Kafka is different: there is no equivalently
standardized instrumentation for `kafka-go`, so `event-worker`'s consumer
manually reads the `traceparent` (and `tracestate`, if present) byte-slice
headers off each `kafka.Message`, builds a `propagation.MapCarrier`, and
calls `otel.GetTextMapPropagator().Extract(ctx, carrier)` before starting
a span — a small, explicit piece of code rather than a dependency for
something this narrow.

**Where the span boundary sits for the Kafka consumer.** One span per
consumed message, started immediately after extraction and ended once
`handle()` returns (success or failure) — matching the granularity of the
existing per-message processing already built in `provider-failover-chain`/
`event-worker`'s job-and-result-channel pattern. No span is created for
Kafka `FetchMessage` itself (that's polling infrastructure, not part of
the traced business operation).

## Risks / Trade-offs

- [Jaeger all-in-one is not how this would run in production — it's an
  in-memory store with no persistence] → correct and intentional; it
  exists solely to prove tracing works locally before Grafana Cloud (a
  real, persistent, hosted backend) replaces it in the follow-up slice.
- [Fixing `RestClient.builder()` → injected `RestClient.Builder` touches
  two already-shipped, working clients] → low risk: the change is
  additive to the constructor (one new injected parameter), the
  request-building code (`.post()`, `.get()`, `.uri()`, etc.) is
  unchanged, and existing controller tests for both clients already mock
  the client at the interface level, not the internal `RestClient`.
- [Manual Kafka header extraction in Go is hand-rolled, not
  library-backed] → small, well-contained (a single function), and the
  W3C `traceparent` format is a stable, simple text format — low
  maintenance risk.

## Migration Plan

Purely additive/instrumentation — no data migration, no endpoint
contract changes. Rollout is the existing CI → GHCR → `rollout restart`
loop per service. Rollback: revert the affected service's image tag;
tracing simply stops being exported again, nothing else regresses.
