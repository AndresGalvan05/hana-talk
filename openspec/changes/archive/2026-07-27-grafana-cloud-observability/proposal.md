## Why

Production core-api has shipped metrics (`/actuator/prometheus`) and OTel
tracing dependencies since M1, but tracing has been explicitly disabled in
the cluster (`OTEL_SAMPLING_PROBABILITY: "0.0"`, with the comment
"No collector in the cluster until M5 — re-point/re-enable at M5") and
nothing has ever scraped the metrics endpoint. The user has since created
a Grafana Cloud account, clearing the external-account blocker this M5
step was waiting on. Rather than ever running Prometheus + Grafana as
in-cluster pods (real RAM cost on a resource-constrained Oracle free-tier
node — the exact thing "saves cluster RAM" in `docs/ROADMAP.md` is about),
core-api ships metrics and traces directly to Grafana Cloud's OTLP
endpoint — no additional in-cluster component at all.

**Scope, decided explicitly**: `ai-exercise-svc` and `event-worker` were
never deployed to the production cluster (`infra/k8s/` has manifests only
for `core-api`, `frontend`, `postgres`, `kafka`, `ingress` — M3/M4 have
only ever run via `docker-compose` locally). Deploying them to production
is real, separate work (new manifests, secrets, ingress/service wiring)
and is explicitly out of scope here. This change wires **core-api only**.

## What Changes

- core-api ships metrics via OTLP to Grafana Cloud, alongside the traces
  it already produces (M5 step 1), using the exact same OTLP-endpoint-plus-
  headers mechanism — no new in-cluster component, no code changes, purely
  Spring Boot configuration:
  - New dependency: `io.micrometer:micrometer-registry-otlp`.
  - `management.otlp.metrics.export.url`, `.enabled` (defaults `false` —
    explicitly opt-in per environment, so local dev/tests are unaffected
    unless configured), and `.headers.Authorization` for Grafana Cloud's
    Basic-auth scheme (instance ID + access policy token, base64-encoded
    by the operator — never computed or seen by this session).
  - `management.otlp.tracing.headers.Authorization` added the same way,
    reusing the existing `management.otlp.tracing.endpoint` property from
    M5 step 1 (defaults to empty, harmless for local Jaeger which needs no
    auth).
- **Local verification against the real Grafana Cloud endpoint**, not just
  trusting config: a new external env file (mirroring
  `~/.config/dev-projects/llm-keys.env`'s pattern —
  `~/.config/dev-projects/grafana-cloud.env`, referenced via
  `${GRAFANA_CLOUD_ENV_PATH}` from a gitignored `infra/.env`, never a
  hardcoded path in a tracked file) lets `docker-compose`'s core-api point
  at the real Grafana Cloud OTLP endpoint locally, so metrics and traces
  arriving in the user's actual Grafana Cloud stack can be confirmed before
  ever touching the production cluster.
- **Production rollout** (executed by the user, not this session — the
  same trust boundary as every other secret creation in this project):
  add the OTLP endpoint URL to `core-api-config` (non-secret, can go in
  the configmap) and the Basic-auth header value to `core-api-secret` (a
  real credential) directly on the cluster, flip
  `OTEL_SAMPLING_PROBABILITY` back up from `0.0`, and set
  `management.otlp.metrics.export.enabled: true`. No manifest/deployment
  changes needed — both are already wired generically via `envFrom`.
- **A dashboard, checked into the repo**: `infra/grafana/core-api-overview.json`,
  a portable Grafana dashboard-as-code definition (request rate, error
  rate, JVM memory, Kafka publish rate — all already-exposed Micrometer
  metrics) the user imports into Grafana Cloud's UI. No Grafana API
  write-access is assumed or required.

## Capabilities

### New Capabilities
- `cloud-observability-export`: core-api's metrics and traces are
  configurable to export to an external OTLP-compatible backend
  (Grafana Cloud in practice) with authentication, independent of and
  without disrupting the local Jaeger-based dev/test setup from M5 step 1.

### Modified Capabilities
(none — `distributed-tracing`'s existing requirements, from
`cross-service-tracing`, are unaffected: local Jaeger export still works
exactly as before; this only adds an additional, optional export target)

## Impact

- **core-api**: `build.gradle.kts` (+1 dependency), `application.yml`
  (new metrics-export properties, new header properties for both signals).
- **infra**: `infra/docker-compose.yml` (env wiring for local verification
  against the real Grafana Cloud endpoint), `infra/.env.example` (documents
  the new `GRAFANA_CLOUD_ENV_PATH` variable alongside the existing
  `LLM_KEYS_ENV_PATH`), new `infra/grafana/core-api-overview.json`.
- **infra/k8s**: `infra/k8s/README.md` gains a rollout note for the
  production configmap/secret changes and the `OTEL_SAMPLING_PROBABILITY`
  flip — executed by the user against the live cluster, not by this
  change's tasks directly (same as every prior secret/credential action in
  this project).
- **Non-goals / cut line**: no deployment of `ai-exercise-svc`/
  `event-worker` to production (separate future change); no Grafana Cloud
  logs (Loki) integration — metrics + traces only, per ROADMAP's explicit
  "dashboards" framing; no alerting rules; no automatic dashboard
  provisioning via Grafana's API (a manual one-time import, like creating
  a k8s secret).
- **Milestone**: M5, per `docs/ROADMAP.md`'s "Order inside milestone"
  (step 2).
