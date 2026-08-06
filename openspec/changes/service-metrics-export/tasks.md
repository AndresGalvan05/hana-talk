## 1. ai-exercise-svc: MeterProvider setup

- [x] 1.1 `app/main.py`: new `MeterProvider` (from
      `opentelemetry.sdk.metrics`), created with the same
      `Resource.create({"service.name": "ai-exercise-svc"})` used for
      the existing `TracerProvider`, wrapped in
      `PeriodicExportingMetricReader(OTLPMetricExporter())`, registered
      via `metrics.set_meter_provider(provider)` — only when
      `os.environ.get("OTEL_METRICS_ENABLED") == "true"`; otherwise a
      no-op `MeterProvider()` is still registered so `meter =
      metrics.get_meter(...)` calls elsewhere never fail regardless of
      the flag
- [x] 1.2 `app/main.py`: module-level `meter = metrics.get_meter
      ("ai-exercise-svc")`, exported for `llm_fallback.py` to import

## 2. ai-exercise-svc: LLM call metrics

- [x] 2.1 `app/llm_fallback.py`: add a module-level
      `_PROVIDER_NAMES = {call_gemini: "gemini", call_groq: "groq",
      call_openrouter: "openrouter"}` lookup and
      `llm_call_duration = meter.create_histogram
      ("llm_call_duration_seconds")`,
      `llm_call_total = meter.create_counter("llm_call_total")`
- [x] 2.2 `app/llm_fallback.py`: inside `call_with_fallback`'s loop,
      wrap the `call_provider(prompt, schema)` + `parse(raw)` pair in a
      `time.monotonic()`-based timer; on success, record the histogram
      and increment the counter with `{"provider": name, "success":
      "true"}`; on exception (existing `except` block), record both
      with `{"provider": name, "success": "false"}` before `continue` —
      no change to the existing control flow or exception handling
      itself
- [x] 2.3 `tests/test_llm_fallback.py` (new file): using a fake
      `MeterProvider`/in-memory metric reader (or monkeypatching
      `llm_fallback.llm_call_duration`/`llm_call_total` with test
      doubles), assert a successful first-provider call records one
      success metric with `provider="gemini"`; assert a first-provider
      failure followed by a second-provider success records one
      failure (`gemini`) and one success (`groq`) metric; assert all
      three providers failing records three failure metrics. Used
      monkeypatching (not a real MeterProvider/InMemoryMetricReader) —
      discovered during implementation that a real test-local provider
      is unreliable here since another test file (`test_routes.py`)
      imports `app.main`, which sets the real global `MeterProvider`
      once per process, and OTel's API silently no-ops subsequent
      `set_meter_provider` calls, so test execution order would
      determine whether a test-local provider actually took effect.
      Monkeypatching the module-level instruments sidesteps that
      entirely. All 3 new tests pass; full suite (28 tests) passes with
      no circular-import or regression issues; `ruff check` clean.
      Also found and fixed a real circular-import bug while
      implementing: the design doc's suggestion to import `meter` from
      `app.main` would have created `main -> routes ->
      generation/chat -> llm_fallback -> main`; fixed by having
      `llm_fallback.py` call `metrics.get_meter(...)` directly via
      OTel's global registry instead (safe due to OTel's documented
      proxy-provider mechanism, which upgrades instruments created
      before `set_meter_provider` runs). Also changed the design's
      dict-keyed-by-function-identity provider-naming approach to
      `(name, callable)` tuples, since `call_gemini`/`call_groq`/
      `call_openrouter` are intentionally re-resolved from module
      globals on every call (an existing, documented pattern so tests
      can monkeypatch them) — a dict built once at import time would
      capture the original function objects as keys and silently fail
      to match a patched callable's identity in tests.

## 3. event-worker: metrics package

- [x] 3.1 New `internal/metrics/metrics.go`: `Setup(ctx context.Context)
      (shutdown func(context.Context) error, err error)`, mirroring
      `tracing.Setup`'s shape — builds an `otlpmetrichttp` exporter from
      `OTEL_EXPORTER_OTLP_ENDPOINT`/`OTEL_EXPORTER_OTLP_HEADERS` (same
      env vars `tracing.go` already reads), wraps it in
      `sdkmetric.NewPeriodicReader(exporter)`, builds a
      `sdkmetric.NewMeterProvider` with the same service-name resource
      `tracing.go` uses, calls `otel.SetMeterProvider(provider)`; if
      `OTEL_METRICS_ENABLED` is not `"true"`, sets a no-op
      `noop.NewMeterProvider()` instead and returns a no-op shutdown —
      mirrors the "always safe to call `meter.RecordX` regardless of the
      flag" approach used on the Python side
- [x] 3.2 `go.mod`: add `go.opentelemetry.io/otel/sdk/metric` and
      `go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetrichttp`
      as direct dependencies (`go get`, then `go mod tidy`)
- [x] 3.3 `cmd/event-worker/main.go`: call `metrics.Setup(ctx)`
      alongside the existing `tracing.Setup(ctx)` call, `defer` its
      shutdown func the same way

## 4. event-worker: Kafka + HTTP metrics

- [x] 4.1 New `internal/consumer/metrics.go` (or inline in
      `consumer.go`): module-level `meter =
      otel.GetMeterProvider().Meter("event-worker")`,
      `processingDuration = meter.Float64Histogram
      ("kafka_message_processing_duration_seconds")`,
      `messagesProcessed = meter.Int64Counter
      ("kafka_messages_processed_total")`
- [x] 4.2 `internal/consumer/consumer.go`'s `processJobs`: wrap the
      existing `err := handle(msgCtx, j.topic, j.value, s)` call with a
      `time.Now()`/`time.Since` timer; record both the histogram and
      counter immediately after, using the same `err != nil` check that
      already drives `span.RecordError(err)`, labeled by `topic` and
      `success`
- [x] 4.3 `internal/api/server.go`: confirm the existing
      `otelhttp.NewHandler` wrapping (already present for tracing) picks
      up the newly-registered global `MeterProvider` automatically for
      HTTP request metrics — no code change expected here, verify via a
      quick read after 3.1/3.3 land, only add explicit instrumentation
      if `otelhttp` turns out to need an option flag for metrics
      specifically. Confirmed via `go doc`:
      `otelhttp.NewHandler`'s docstring states it "wraps the passed
      handler in a span named after the operation and enriches it with
      metrics" — uses the default global `MeterProvider` (no
      `WithMeterProvider` option passed in `main.go`), which
      `metrics.Setup` already registers before the HTTP server is
      constructed. No code change needed.
- [x] 4.4 `internal/consumer/metrics_test.go` (new): using a manual
      Go OTel SDK reader (`sdkmetric.NewManualReader()` +
      `sdkmetric.NewMeterProvider(sdkmetric.WithReader(...))` set as the
      global provider for the test), assert processing a fake successful
      message records one success metric for its topic; assert a
      `handle()` error records one failure metric for its topic. Found a
      real Go OTel API constraint while writing this: the global
      MeterProvider's proxy mechanism only upgrades this package's
      already-created instruments to a real backing implementation the
      *first* time `otel.SetMeterProvider` is called process-wide — a
      second call in a later, separate test doesn't retarget them, so
      an initial two-separate-tests design silently lost data in the
      second test. Fixed by combining both the success and failure
      assertions into one test function sharing a single
      `SetMeterProvider` call. Full event-worker suite green:
      `go build`, `go vet`, `go test ./...`, `gofmt -l .` all clean.

## 5. Config wiring

- [x] 5.1 `infra/k8s/ai-exercise-svc/configmap.yaml`: add
      `OTEL_METRICS_ENABLED: "true"`, with a comment noting this is
      distinct from core-api's `GRAFANA_CLOUD_METRICS_ENABLED` (OTel SDK
      directly vs. Spring/Micrometer)
- [x] 5.2 `infra/k8s/event-worker/configmap.yaml`: same
      `OTEL_METRICS_ENABLED: "true"` addition with the same comment
- [x] 5.3 Confirm `infra/docker-compose.yml` leaves `OTEL_METRICS_ENABLED`
      unset for both services (defaults to disabled locally, avoiding
      the Jaeger-doesn't-accept-metrics problem) — no edit expected, just
      verify during task 6. Confirmed: neither service's `environment:`
      block sets it; no edit made.

## 6. Tests & lint

- [x] 6.1 `ai-exercise-svc`: `pytest` and `ruff check` green
- [x] 6.2 `event-worker`: `go build ./...`, `go vet ./...`, `go test
      ./...`, `gofmt -l .` all clean

## 7. Local verification

- [x] 7.1 `docker compose up -d --build`; confirm both services start
      normally with `OTEL_METRICS_ENABLED` unset (no crash, no error
      logs about failed metric export attempts) — this is the local
      confirmation that the no-op-provider-when-disabled path works.
      Verified: both containers came up cleanly, logs show only normal
      startup lines, no metrics-related errors.
- [x] 7.2 Temporarily set `OTEL_METRICS_ENABLED=true` for one service
      locally (pointed at the local Jaeger endpoint, which will reject
      or silently drop OTLP metrics since Jaeger only accepts traces)
      and confirm the service still starts and runs without crashing or
      blocking — this confirms the exporter failure mode is non-fatal,
      matching the app's established "observability is best-effort,
      never blocks core functionality" principle (documented in
      CLAUDE.md re: Kafka publishing). Verified for BOTH services (not
      just one): ran each via `docker compose run` with
      `OTEL_METRICS_ENABLED=true` against the local Jaeger endpoint,
      waited past the default ~60s periodic-export interval — both
      stayed up the whole time with no crash, no unhandled exception,
      no crash-loop.
- [x] 7.3 Trigger a chat message and an exercise-generation request
      against local `ai-exercise-svc`; trigger a lesson completion so
      `event-worker` processes a Kafka message; confirm via unit test
      execution (task 2.3/4.4) that the metric-recording code paths are
      exercised correctly — real Grafana Cloud export itself can only be
      confirmed in production per the design doc's accepted limitation.
      Verified all three against the real local stack: a chat message
      (200, real reply), a lesson completion (204, event-worker logged
      no errors), and exercise generation (200, 8 real exercises —
      after clearing a stale/pre-existing MongoDB cache entry from an
      earlier session, unrelated to this change, that was failing
      Pydantic validation on read). All three exercise the exact
      `call_with_fallback`/`processJobs` code paths the new unit tests
      cover.

## 8. Production rollout

- [ ] 8.1 Deploy — merge to `main`, CI builds `ai-exercise-svc` and
      `event-worker` images, `kubectl rollout restart` both (no
      migration)
- [ ] 8.2 Confirm both services start cleanly in production logs (no
      crash-looping from the new metrics setup code)
- [ ] 8.3 Trigger real activity (a chat message, an exercise generation,
      a lesson completion) against production, then check the Grafana
      Cloud metrics browser/Explore view for `llm_call_duration_seconds`,
      `llm_call_total`, `kafka_message_processing_duration_seconds`,
      `kafka_messages_processed_total`, and standard `http_server_*`
      metrics from both services — this is the only real confirmation
      that OTLP export actually reaches Grafana Cloud, per the design
      doc's accepted local-verification limitation

## 9. Docs

- [ ] 9.1 Update `docs/DEVLOG.md` (session entry) and `docs/ROADMAP.md`
      decision log — including correcting the stale "extended
      observability still on the table" framing now that tracing is
      confirmed already done and only metrics were the actual gap
- [ ] 9.2 Update `docs/ARCHITECTURE.md` §8 (or wherever the "core-api,
      ai-exercise-svc, and event-worker all export OTel traces (core-api
      also exports metrics)" line lives) to reflect all three now
      exporting metrics
