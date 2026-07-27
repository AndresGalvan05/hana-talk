## 1. core-api dependency and config

- [x] 1.1 Add `io.micrometer:micrometer-registry-otlp` to
      `build.gradle.kts`
- [x] 1.2 `application.yml`: add `management.otlp.metrics.export.url`,
      `.enabled` (default `false`), `.headers.Authorization` (default
      empty)
- [x] 1.3 `application.yml`: add `management.otlp.tracing.headers.Authorization`
      (default empty) alongside the existing `management.otlp.tracing.endpoint`
- [x] 1.4 `sh gradlew ktlintCheck test bootJar` green

## 2. Local verification wiring

- [x] 2.1 Add `GRAFANA_CLOUD_ENV_PATH` support to
      `infra/docker-compose.yml`'s `core-api` service (`env_file`,
      mirroring `ai-exercise-svc`'s `LLM_KEYS_ENV_PATH` pattern) — must
      tolerate the variable being unset for everyone who hasn't configured
      Grafana Cloud locally
- [x] 2.2 Document the expected shape (`OTEL_EXPORTER_OTLP_ENDPOINT`,
      `OTEL_EXPORTER_OTLP_METRICS_URL` or equivalent, and the Authorization
      header value) in `infra/.env.example`, alongside the existing
      `LLM_KEYS_ENV_PATH` documentation — no real values, just the
      variable names and format

## 3. Dashboard

- [x] 3.1 `infra/grafana/core-api-overview.json`: a Grafana dashboard
      definition with panels for request rate, error rate, JVM memory, and
      Kafka publish rate, built from core-api's existing Micrometer
      metrics

## 4. Local live verification (requires the user's real Grafana Cloud credentials)

- [x] 4.1 Ask the user to create `~/.config/dev-projects/grafana-cloud.env`
      (external, outside the repo, same pattern as `llm-keys.env`) with
      their real Grafana Cloud OTLP endpoint and Basic-auth header value —
      this session must never read its contents directly
- [x] 4.2 `docker compose up -d --build` with `GRAFANA_CLOUD_ENV_PATH` set;
      generate some core-api traffic; confirm metrics and traces actually
      arrive in the user's real Grafana Cloud stack (the user checks their
      own Grafana Cloud UI, since this session has no access to it)
- [x] 4.3 Import `core-api-overview.json` into Grafana Cloud and confirm
      its panels render real data

## 5. Production rollout (documented, executed by the user against the live cluster)

- [x] 5.1 Document in `infra/k8s/README.md`: add the OTLP metrics URL to
      `core-api-config`, add the Authorization header value to
      `core-api-secret`, flip `OTEL_SAMPLING_PROBABILITY` up from `"0.0"`,
      set `management.otlp.metrics.export.enabled` (via configmap) to
      `"true"` — exact key names, no values, matching how every prior
      cluster secret in this project was documented rather than scripted
- [x] 5.2 (User-executed, not a task this session runs) Apply the updated
      configmap/secret on the cluster and roll out core-api; confirm
      metrics/traces appear in Grafana Cloud from production traffic —
      done 2026-07-27: `core-api-secret` patched with
      `OTEL_EXPORTER_OTLP_AUTH`, configmap applied, core-api rolled out;
      confirmed via absence of the previously-constant 401 export errors
      across a full metrics push cycle (see DEVLOG 2026-07-27)

## 6. Docs

- [x] 6.1 Update `docs/ROADMAP.md` (this M5 step done) and `docs/DEVLOG.md`
      (session entry)
