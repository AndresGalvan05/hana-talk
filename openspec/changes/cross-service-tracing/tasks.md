## 1. Local collector

- [x] 1.1 Add a `jaeger` service to `infra/docker-compose.yml`
      (`jaegertracing/all-in-one`, OTLP HTTP port `4318` exposed
      internally, UI port `16686` published to the host)
- [x] 1.2 Point core-api's `OTEL_EXPORTER_OTLP_ENDPOINT` at
      `http://jaeger:4318` in compose

## 2. core-api

- [x] 2.1 Add `spring.kafka.template.observation-enabled: true` to
      `application.yml`
- [x] 2.2 `AiExerciseSvcClient`: inject the auto-configured
      `RestClient.Builder` bean instead of calling `RestClient.builder()`,
      keeping the existing per-service timeout customization
- [x] 2.3 `EventWorkerClient`: same fix
- [x] 2.4 `sh gradlew ktlintCheck test bootJar` green (existing controller
      tests mock these clients at the interface level, so no test changes
      expected — confirm that assumption holds)

## 3. ai-exercise-svc

- [x] 3.1 Add `opentelemetry-sdk`, `opentelemetry-exporter-otlp-proto-http`,
      `opentelemetry-instrumentation-fastapi` dependencies
- [x] 3.2 SDK setup in `app/main.py`: OTLP exporter pointed at
      `OTEL_EXPORTER_OTLP_ENDPOINT` (new env var/setting), FastAPI
      auto-instrumentation applied to the app
- [x] 3.3 `uv run pytest` / `ruff check` / `ruff format --check` still
      green (instrumentation should not change existing test behavior)

## 4. event-worker

- [x] 4.1 Add `go.opentelemetry.io/otel`,
      `go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp`,
      `go.opentelemetry.io/otel/sdk`,
      `go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp`
      dependencies
- [x] 4.2 SDK setup in `main.go`: tracer provider + OTLP exporter pointed
      at a new `OTEL_EXPORTER_OTLP_ENDPOINT` config value
- [x] 4.3 Wrap the internal API server (`internal/api`) with `otelhttp` so
      incoming HTTP requests from core-api continue the trace
- [x] 4.4 In the Kafka consumer: extract `traceparent`/`tracestate` from
      `kafka.Message.Headers` into a `propagation.MapCarrier`, call
      `otel.GetTextMapPropagator().Extract`, and start a span as a child
      of that context before calling `handle()`
- [x] 4.5 `go build`/`go vet`/`go test` still green

## 5. Infra wiring for the two new services

- [x] 5.1 Add `OTEL_EXPORTER_OTLP_ENDPOINT: http://jaeger:4318` to
      `ai-exercise-svc` and `event-worker` in `infra/docker-compose.yml`,
      with both services depending on `jaeger`

## 6. Verification

- [x] 6.1 `docker compose up -d --build`: complete a lesson, confirm a
      single trace in the Jaeger UI (`localhost:16686`) spans the HTTP
      request into core-api and `event-worker`'s consumption of the
      resulting `exercise.completed` message
- [x] 6.2 Request exercises for a lesson with none yet, confirm a trace
      spans core-api's request and `ai-exercise-svc`'s `/generate` span
- [x] 6.3 Request the leaderboard or streak endpoint, confirm a trace
      spans core-api's request and `event-worker`'s internal API span
- [x] 6.4 Update `docs/ROADMAP.md` (this M5 slice done), `docs/DEVLOG.md`
      (session entry, including the `RestClient.builder()` finding as a
      root-caused lesson)
