## Context

core-api's `application.yml` already has (from M1 and `cross-service-tracing`):
```yaml
management:
  tracing:
    sampling:
      probability: ${OTEL_SAMPLING_PROBABILITY:1.0}
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}
```
and `build.gradle.kts` already has `micrometer-registry-prometheus`,
`micrometer-tracing-bridge-otel`, and `opentelemetry-exporter-otlp`.
Production's `infra/k8s/core-api/configmap.yaml` sets
`OTEL_SAMPLING_PROBABILITY: "0.0"` with a comment saying to re-point at
M5 — there is genuinely no collector reachable from the cluster today.
`core-api-secret` (DB credentials, JWT secret) and `core-api-config`
(non-secret settings) are both already wired into the Deployment via
`envFrom`, so no manifest changes are needed to add more keys to either.

## Goals / Non-Goals

**Goals:**
- Metrics and traces both reach Grafana Cloud with zero additional
  in-cluster components (no Grafana Agent/Alloy, no Prometheus, no
  Grafana server pod) — the entire point of "saves cluster RAM."
- Verify the wiring actually works against the user's real Grafana Cloud
  stack before touching production, using the same external-secrets-file
  pattern already established for LLM keys.
- Local dev (docker-compose without the Grafana Cloud env file set) and
  the existing test suite are completely unaffected.

**Non-Goals:**
- Deploying `ai-exercise-svc`/`event-worker` to production (explicitly
  deferred, per the scoping decision in proposal.md).
- Logs/Loki integration, alerting, automatic dashboard provisioning via
  API.

## Decisions

**OTLP direct from core-api, not a Grafana Agent/Alloy sidecar or
DaemonSet.** Grafana Cloud accepts metrics via its OTLP Gateway just as
readily as via Prometheus remote_write. Since core-api already has the
OTel SDK and an OTLP trace exporter wired up (M5 step 1), adding
`micrometer-registry-otlp` for metrics reuses the exact same transport,
endpoint, and auth-header mechanism — one exporter path for both signals,
zero new moving parts in the cluster. Rejected alternative: run Grafana
Alloy to scrape `/actuator/prometheus` and remote_write to Grafana Cloud.
Rejected because it's an extra pod (RAM, exactly what this change exists
to avoid) doing a translation core-api can do natively.

**`management.otlp.metrics.export.enabled` defaults to `false`.** Simply
having `micrometer-registry-otlp` on the classpath would otherwise
auto-enable metrics export (Spring Boot's `@ConditionalOnEnabledMetricsExport`
convention defaults `true`) and start POSTing to an empty/local URL in
every dev machine and CI test run — explicit opt-in avoids that
new noise source entirely, unlike the already-accepted OTLP-trace noise
from earlier milestones (a deliberate choice not to repeat that pattern
where avoidable).

**Verify locally against the real Grafana Cloud endpoint before ever
touching the production cluster.** Mirrors the exact pattern already used
for `~/.config/dev-projects/llm-keys.env`: a new external file
(`~/.config/dev-projects/grafana-cloud.env`), referenced via
`${GRAFANA_CLOUD_ENV_PATH}` from a gitignored `infra/.env`, so
`docker-compose`'s core-api can point at the real endpoint and the user's
actual Grafana Cloud stack can be checked for incoming data — without ever
touching production, and without this session ever reading the raw
instance ID or API token.

**Production rollout is the user's action, not a task this session
executes.** Every prior secret in this project (`core-api-secret`, the
Cloudflare Origin CA TLS secret, the LLM keys file) has been created
directly by the user, never via a command this session ran with the raw
value visible. The Grafana Cloud Basic-auth header (base64 of
`instanceId:apiToken`) is exactly that kind of value — the user computes
it and creates/updates `core-api-secret` on the cluster themselves.
`docs/ROADMAP.md`/`infra/k8s/README.md` document the exact keys to add,
not the values.

**A checked-in dashboard JSON, not API-provisioned dashboards.** Grafana's
dashboard JSON model is portable and importable through the UI in one
click, needs no Grafana API token with write access (a credential this
change would otherwise need to touch), and gives a durable, reviewable
artifact in the repo — consistent with the project's existing "config and
manifests in the repo, secrets never in the repo" split.

## Risks / Trade-offs

- [Grafana Cloud's free tier has retention/volume limits] → acceptable;
  a portfolio-scale demo's traffic is nowhere near any reasonable free-tier
  cap, and this isn't a production SLA commitment.
- [core-api's own OTLP export failing (network blip, Grafana Cloud
  hiccup) could add latency/log noise] → mitigated the same way trace
  export already is: async, batched, best-effort — a failed metrics/trace
  export never blocks or fails a user-facing request, matching Micrometer's
  existing behavior for the Prometheus registry and the OTLP trace exporter.
- [The Basic-auth header value is a single opaque string in a k8s Secret]
  → matches the existing `core-api-secret` pattern exactly; no new secret
  management approach introduced.

## Migration Plan

No schema/data changes. Rollout: user updates `core-api-config`/
`core-api-secret` on the cluster (documented keys, not automated), flips
`OTEL_SAMPLING_PROBABILITY` up, restarts the deployment. Rollback: revert
the configmap/secret keys and sampling value; core-api behaves exactly as
it did before this change (metrics/traces simply stop exporting anywhere,
same as today).
