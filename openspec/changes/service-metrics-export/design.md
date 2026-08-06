## Context

Confirmed via direct code reads: `core-api` already gates its OTLP
metrics export behind an explicit opt-in
(`application.yml`'s `management.otlp.metrics.export.enabled:
${GRAFANA_CLOUD_METRICS_ENABLED:false}`), with a code comment
explaining why — Spring Boot's `@ConditionalOnEnabledMetricsExport`
defaults to `true` once the OTLP registry is on the classpath, which
would otherwise make every local/CI run try to POST metrics to an empty
URL. Local `docker-compose.yml` points both `ai-exercise-svc` and
`event-worker`'s `OTEL_EXPORTER_OTLP_ENDPOINT` at the local Jaeger
container (`http://jaeger:4318`) — but Jaeger's `all-in-one` image only
accepts OTLP **traces**, not metrics, so a metrics exporter enabled
locally would either error or silently drop data. This app's existing
convention (explicit opt-in, disabled by default, enabled only via a
production env var) directly solves that problem and is what this
change mirrors for both new services, rather than inventing a different
gating mechanism per language.

`ai-exercise-svc/app/main.py` already creates a `TracerProvider` and
registers it globally; `opentelemetry-sdk` (already a dependency)
bundles `opentelemetry.sdk.metrics`, and
`opentelemetry-exporter-otlp-proto-http` (already a dependency) bundles
`opentelemetry.exporter.otlp.proto.http.metric_exporter.OTLPMetricExporter`
— no new PyPI package needed.

`event-worker/internal/tracing/tracing.go` already creates a
`TracerProvider` via `otlptracehttp` + `sdktrace.NewTracerProvider`,
wired into `main.go` via `tracing.Setup(ctx)`. `go.mod` already has
`go.opentelemetry.io/otel/metric` as an indirect dependency (pulled in
transitively) but lacks the two packages actually needed to build and
export metrics: `go.opentelemetry.io/otel/sdk/metric` (a separate Go
module from the trace SDK) and
`go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetrichttp`.

`call_with_fallback` (`ai-exercise-svc/app/llm_fallback.py`) is the
single chokepoint every LLM call already passes through — a `for`
loop over `(call_gemini, call_groq, call_openrouter)`, each attempt in
its own `try/except`, `return parse(raw)` on success inside the `try`,
`continue` to the next provider on any exception. `event-worker`'s
`processJobs` (`internal/consumer/consumer.go`) already wraps every
`handle()` call in a tracing span (`tracer.Start`/`span.End`) — the
natural, already-instrumented site to add a parallel duration/counter
recording without touching `handle()` itself.

## Goals / Non-Goals

**Goals:**
- Real operational visibility into the two areas that actually carry
  risk: LLM provider latency/failure rate, and Kafka consumer
  throughput/errors — plus baseline HTTP metrics that come essentially
  free from libraries already in use.
- Reuse every piece of existing OTLP config (endpoint, headers) — zero
  new secrets.
- Match the existing "explicit opt-in, safe by default" gating pattern
  core-api already established, rather than introducing a second
  gating philosophy.

**Non-Goals:** see proposal.md — no dashboards, no alerting, no
database-level metrics, no cross-language metric-naming unification.

## Decisions

- **Metrics export gated by a new `OTEL_METRICS_ENABLED` env var,
  default `false` in both services** — directly mirroring core-api's
  `GRAFANA_CLOUD_METRICS_ENABLED` pattern and its stated reason (avoid
  local/CI runs silently trying to export against an endpoint that can't
  accept metrics). Named generically (`OTEL_METRICS_ENABLED`, not
  `GRAFANA_CLOUD_...`) since these two services use the standard OTel
  SDK directly rather than Spring/Micrometer's Grafana-specific naming.
  Local `docker-compose.yml` leaves it unset (`false`); production k8s
  configmaps set it to `"true"`.
- **`ai-exercise-svc`**: new `Resource`-scoped `MeterProvider` in
  `main.py`, immediately after the existing `TracerProvider` setup —
  `PeriodicExportingMetricReader(OTLPMetricExporter())` wrapped in the
  same `if os.environ.get("OTEL_METRICS_ENABLED") == "true":` guard.
  `FastAPIInstrumentor.instrument_app(app)` (already called) will start
  emitting HTTP request metrics automatically once a global
  `MeterProvider` exists — no separate instrumentation call needed for
  that part.
- **`ai-exercise-svc` LLM metrics live inside `call_with_fallback`**, not
  duplicated per-provider-function — a `meter.create_histogram(
  "llm_call_duration_seconds")` and `meter.create_counter(
  "llm_call_total")` (labeled `provider`, `success`), recorded around
  each provider attempt: `start = time.monotonic()` before
  `call_provider(prompt, schema)`, duration recorded in both the success
  path (right after `return parse(raw)`'s value is computed, before
  actually returning — Python doesn't have a clean "record on return"
  without restructuring, so this uses a small local wrapper instead of
  inlining into the `try` body directly, keeping `call_with_fallback`'s
  existing control flow untouched) and the `except` path (before
  `continue`). Provider name comes from a small
  `{call_gemini: "gemini", call_groq: "groq", call_openrouter:
  "openrouter"}` lookup dict, since the functions themselves have no
  name attribute suitable for a metric label today.
- **`event-worker` gets a new sibling package `internal/metrics/`**
  (not folded into `internal/tracing/`) — `metrics.Setup(ctx)` returns a
  shutdown func exactly like `tracing.Setup(ctx)` does, called
  side-by-side in `main.go`. Kept separate rather than merged into
  `tracing.go` because the two have genuinely different SDK types
  (`TracerProvider` vs `MeterProvider`) and separate lifecycle
  functions read more clearly than one file doing two unrelated SDK
  setups.
- **`event-worker` Kafka metrics recorded in `processJobs`**, wrapping
  the existing `err := handle(msgCtx, j.topic, j.value, s)` call: a
  histogram (`kafka_message_processing_duration_seconds`, labeled
  `topic`) and a counter (`kafka_messages_processed_total`, labeled
  `topic`, `success`) recorded immediately after, using the same `err
  != nil` check that already drives `span.RecordError(err)` — one
  additional metrics-recording block next to the existing
  tracing block, not a restructure.
- **`event-worker` HTTP metrics via `otelhttp`'s existing wrapping**:
  `internal/api/server.go`'s handler is already wrapped with
  `otelhttp.NewHandler` for tracing (confirmed in earlier research) —
  once a global `MeterProvider` is registered, `otelhttp` emits standard
  HTTP server metrics automatically; no separate instrumentation call
  needed, matching the FastAPI situation.
- **Metric naming follows standard OTel/Prometheus convention**
  (`_seconds` histograms, `_total` counters), explicitly *not* matching
  core-api's Micrometer-specific `_milliseconds_count` suffix — these
  are different SDKs with different idiomatic conventions, and forcing
  one language's naming quirk onto another would be worse than
  following each ecosystem's own norm. Documented explicitly so a future
  reader doesn't "fix" this as an inconsistency.

## Risks / Trade-offs

- **[Risk] No local way to visually confirm exported metrics** (Jaeger
  doesn't accept OTLP metrics; no local Prometheus/collector exists).
  Accepted — mirrors how core-api's own metrics were originally verified
  (per this session's DEVLOG: confirmed live against the real Grafana
  Cloud metrics browser after deploy, not locally). Local verification
  for this change instead confirms the instrumentation code runs
  without error when the flag is on (a quick manual toggle locally,
  observing no crash/exception), and that provider/topic labels are
  correct by inspecting the recorded metric objects directly in a unit
  test, not by seeing them rendered anywhere.
- **[Risk] `call_with_fallback`'s success-path duration recording
  requires a small structural tweak** (capturing duration around
  `call_provider(...)` specifically, not the whole loop iteration,
  since `parse(raw)` failures should count as that provider's failure
  too — matching existing behavior where a schema-parse failure already
  triggers fallback to the next provider). Mitigated by keeping the
  change to the smallest possible diff: wrap only the
  `call_provider(prompt, schema)` + `parse(raw)` pair in one timed
  block, unchanged control flow otherwise.
- **[Risk] `OTEL_METRICS_ENABLED` is a new env var name, distinct from
  core-api's `GRAFANA_CLOUD_METRICS_ENABLED`.** Accepted — see Decisions;
  naming reflects that these services use the OTel SDK directly, not a
  Grafana-Cloud-specific Spring integration. Documented in both
  services' k8s configmap comments so it's not mistaken for the same
  variable.

## Migration Plan

None — no database change, no schema. New env var
(`OTEL_METRICS_ENABLED`) added to `infra/k8s/ai-exercise-svc/configmap.yaml`
and `infra/k8s/event-worker/configmap.yaml`, both set to `"true"` in
production; left unset (defaulting to `false`) in local
`docker-compose.yml`. Deploys through the existing CI → GHCR →
`kubectl rollout restart` paths for both services.
