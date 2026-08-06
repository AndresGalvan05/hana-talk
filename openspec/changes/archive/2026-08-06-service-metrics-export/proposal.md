## Why

Research before writing this proposal corrected an assumption: tracing
for `ai-exercise-svc` and `event-worker` is **already live in Grafana
Cloud in production**, shipped by `cross-service-tracing` earlier this
session — `docs/ARCHITECTURE.md` §8 confirms all three services export
OTel traces. The `docs/ROADMAP.md` line framing this as "still on the
table" was stale. The real, remaining gap is narrower: **only core-api
exports metrics** (via Micrometer → OTLP). Neither `ai-exercise-svc` nor
`event-worker` records or exports a single metric today — no request
counts, no LLM call latency, no Kafka processing throughput. For a
service whose core operational risk is a slow or failing LLM provider
chain, and a service whose core job is consuming Kafka events reliably,
that's a real observability blind spot, not a nice-to-have.

## What Changes

- `ai-exercise-svc` gets an OTel `MeterProvider` alongside its existing
  `TracerProvider`, recording LLM call duration and success/failure per
  provider (Gemini/Groq/OpenRouter) from inside `call_with_fallback`,
  plus FastAPI's built-in request count/latency instrumentation (already
  available via the existing `FastAPIInstrumentor`, just not currently
  emitting metrics because no `MeterProvider` is registered).
- `event-worker` gets a parallel OTel metrics setup alongside its
  existing tracing setup, recording Kafka messages processed per topic,
  processing duration, and processing errors from the consumer's
  existing per-message span site — plus standard HTTP request metrics
  on its small internal API (`/leaderboard`, `/users/{id}/streak`,
  `/users/{id}/achievements`) via `otelhttp`'s existing instrumentation
  (already wrapping the handler for tracing, extended to also emit
  metrics).
- Metrics export reuses the exact same `OTEL_EXPORTER_OTLP_ENDPOINT`/
  `OTEL_EXPORTER_OTLP_HEADERS` env vars each service already has for
  traces — no new secrets, no new k8s config beyond a feature-flag
  toggle mirroring core-api's existing `GRAFANA_CLOUD_METRICS_ENABLED`
  pattern.

## Capabilities

### New Capabilities
- `service-metrics-export`: metrics instrumentation and export for
  `ai-exercise-svc` and `event-worker`.

### Modified Capabilities
(none — additive instrumentation, no existing behavior changes)

## Impact

- `ai-exercise-svc/app/main.py`: new `MeterProvider` setup alongside the
  existing `TracerProvider`.
- `ai-exercise-svc/app/llm_fallback.py`: records a duration histogram +
  success/failure counter per provider attempt inside `call_with_fallback`.
- `event-worker/internal/tracing/` (or a new sibling `internal/metrics/`
  package — decided in design): parallel `MeterProvider` setup.
- `event-worker/internal/consumer/consumer.go`: records processing
  duration + success/error counters per topic in `processJobs`, at the
  same site that already wraps `handle()` in a tracing span.
- `event-worker/internal/api/server.go`: `otelhttp` handler wrapping
  extended to also emit metrics (already wraps for tracing).
- `event-worker/go.mod`: adds `go.opentelemetry.io/otel/sdk/metric` and
  `go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetrichttp`.
  `ai-exercise-svc/pyproject.toml`: **no new package** — the metrics SDK
  and OTLP metric exporter are already bundled in the `opentelemetry-sdk`
  and `opentelemetry-exporter-otlp-proto-http` packages already installed
  for tracing.
- No k8s manifest changes beyond one new boolean env var per service
  (metrics-export toggle), reusing existing OTLP endpoint/header config.

## Non-goals / cut line

- **No new Grafana Cloud dashboards or panels** for these two services —
  confirmed explicitly with the user: metrics export only, dashboards
  are a separate future step once real metric data exists to build
  panels against.
- **No alerting rules.**
- **No metrics for `ai-exercise-svc`'s MongoDB calls** or `event-worker`'s
  Postgres calls — scoped to the two areas the user confirmed matter
  most (LLM call latency/failures, Kafka throughput) plus the baseline
  HTTP metrics that come essentially free from already-present
  instrumentation libraries. Database-level metrics are a reasonable
  future addition, not required here.
- **No unification with core-api's Micrometer-style metric naming**
  (`_milliseconds_count` suffix convention). Python/Go use native OTel
  SDKs, not Micrometer, so they follow standard OTel/Prometheus
  convention instead (`_seconds` base unit, `_count`/`_sum`/`_bucket` for
  histograms) — a deliberate per-language-SDK convention difference, not
  an inconsistency to reconcile.

## Milestone

Post-roadmap. Closes out the last item from the post-roadmap planning
session's original list (rate limiting, achievement system, admin UI,
extended observability) — though, per the corrected framing above, it
turned out to be a smaller gap than that list implied, since tracing was
already done.
