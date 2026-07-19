# Design — deploy core stack to k3s on Oracle Always Free

## Context

M1 runs locally via docker compose (postgres, single-node KRaft Kafka, core-api)
plus `npm run dev` for the frontend. CI already lint/test/builds both services
and pushes a core-api image to GHCR — but amd64-only. `infra/k8s/` contains
draft manifests for core-api and postgres (namespace `dev`, never applied to a
real cluster) and a README that says "not started". core-api is fully
env-driven; the frontend resolves its API base from `VITE_API_URL` at build
time (default `http://localhost:8080`).

Target: one Oracle Always Free A1 Flex VM (aarch64, 4 OCPU / 24 GB) running
single-node k3s, serving the M1 slice at `https://hanatalk.online`.

## Goals / Non-Goals

**Goals:**

- The full M1 flow (register → login → course → lessons → complete → progress)
  works at `https://hanatalk.online` on a fresh browser.
- CI on main produces multi-arch (amd64 + arm64) GHCR images for core-api and
  frontend; getting them onto the cluster is one documented command.
- Everything on the cluster is reproducible from `infra/k8s/` + a runbook +
  manually-created Secrets. A dead VM can be rebuilt in under an hour.
- Local docker compose workflow stays untouched.

**Non-Goals:**

- GitOps, Helm, autoscaling, multi-node, dev/prod split, cert-manager,
  in-cluster observability, external secret managers (see proposal cut line).
- Zero-downtime deploys — single node, portfolio project; brief downtime on
  rollout is accepted and documented.

## Decisions

### D1 — Single namespace `hanatalk`, not `dev`/`prod`

The draft manifests use `dev` and the old README promised a dev/prod split.
There is one cluster and one environment; a namespace split with nothing in it
is ceremony. Rename to `hanatalk`. The split returns if a second environment
ever exists (same trigger as Helm adoption).
*Alternative — keep `dev`:* misleading name for the thing that is publicly
`prod`.

### D2 — Multi-arch images via buildx, not arm-only and not arm runners

A1 Flex is aarch64; today's images won't run there. Both workflows switch to
`docker/setup-qemu-action` + `docker/setup-buildx-action` +
`docker/build-push-action` with `platforms: linux/amd64,linux/arm64`. The
expensive work (Gradle bootJar, `npm run build`) already happens on the runner
before/outside the image build; the Dockerfiles only copy artifacts onto a base
image, so QEMU emulation cost is negligible.
*Alternatives:* arm64-only (breaks local `docker compose up` on the x86 dev
machine); native `ubuntu-24.04-arm` runner matrix (two jobs + manifest
stitching — more CI complexity for zero portfolio value here).

### D3 — Frontend: nginx static image, `VITE_API_URL` baked empty

Multi-stage Dockerfile: `node:24-alpine` build stage (`npm ci && npm run build`
with `VITE_API_URL=""`) → `nginx:alpine` serving `dist/` with an SPA fallback
(`try_files $uri /index.html`). Empty API base makes the app call same-origin
`/api/...`, which the ingress routes to core-api — production traffic never
triggers CORS. `CORS_ALLOWED_ORIGINS` stays configured for local dev only.
*Alternative — runtime env injection (envsubst into a config.js):* solves a
multi-env problem we don't have; adds moving parts.

### D4 — Standard `Ingress` resource on bundled Traefik, single host

One Ingress for `hanatalk.online`: `/api` → `core-api:8080`, `/` → `frontend:80`.
Actuator endpoints are NOT routed — health stays cluster-internal for probes.
The standard `networking.k8s.io/v1` Ingress is enough for host+path routing and
is more transferable knowledge than Traefik's `IngressRoute` CRD.
*Alternative — IngressRoute CRD:* Traefik-specific API for no added capability
at this scope.

### D5 — TLS: Cloudflare proxy in Full (strict) with an Origin CA certificate

Cloudflare proxies DNS and terminates public TLS. Origin leg: a Cloudflare
Origin CA cert (15-year validity) created once in the dashboard, applied
manually as a `kubernetes.io/tls` Secret referenced by the Ingress. Consistent
with the "plain Secrets applied manually" simplification and avoids running
cert-manager.
*Alternatives:* Flexible mode (plaintext origin — weak story in a security
question); cert-manager + DNS-01 (a controller + API token to babysit; deferred
until origin certs are actually painful).

### D6 — Kafka and postgres as single-replica StatefulSets

Both move to StatefulSets with `volumeClaimTemplates` on k3s's default
`local-path` StorageClass. For Kafka, stable pod identity matters even at
n=1 (broker id / advertised listener `kafka:9092`, cluster metadata in the
PVC). Postgres converts from the draft Deployment for the same reason plus a
practical one: a Deployment's rolling update deadlocks on an RWO PVC, and
StatefulSet restarts are the idiomatic fix. Kafka runs KRaft combined mode
(`apache/kafka:3.9.1`, same as compose), internal listener only,
`auto.create.topics.enable=true` as today.
*Alternative — Deployments with `strategy: Recreate`:* works, but the
StatefulSet answer is the one you want to give in an interview.

### D7 — Deploys are `kubectl rollout restart` on `:latest`, pull-by-digest deferred

Manifests reference `:latest` with `imagePullPolicy: Always`; the documented
deploy step is `kubectl -n hanatalk rollout restart deploy/core-api` (or
frontend) after CI pushes. Not reproducible, and the runbook says so explicitly
— pinning by sha/digest (or GitOps) is the named next step if this ever bites.
GHCR packages are made public so no `imagePullSecrets` are needed.

### D8 — Observability off in-cluster: `OTEL_SAMPLING_PROBABILITY=0.0`

The draft ConfigMap pointed OTLP at an `otel-collector` that won't exist until
M5; leaving sampling at 1.0 would spam export failures in logs. Set sampling to
0.0 and drop the endpoint override. Prometheus endpoint stays exposed on the
actuator (cluster-internal only) for M5.

### D9 — VM access and network exposure

Only 80/443 are opened to the internet (Oracle NSG/security list + host
iptables — Oracle Ubuntu images ship restrictive iptables rules, a documented
runbook step). The k3s API (6443) is never public: `kubectl` runs over SSH
(or an SSH tunnel from the laptop with the copied kubeconfig). SSH stays
key-only on 22.

## Risks / Trade-offs

- [A1 Flex capacity is notoriously scarce ("out of capacity" on create)] →
  runbook notes: retry across availability domains / times of day; this blocks
  the whole milestone, so VM creation is task #1, done by the user, before any
  manifest work is needed on-cluster.
- [Oracle reclaims idle Always Free instances] → the deployed stack generates
  steady baseline activity (JVM, Kafka); runbook notes the reclamation policy
  and that a rebuild-from-runbook takes <1h. PVC data (users/progress) is
  demo-grade; Flyway re-seeds content.
- [`:latest` deploys are not reproducible] → accepted (D7), documented, with
  digest-pinning as the escape hatch.
- [Single node: every rollout is brief downtime] → accepted; portfolio project.
- [Kafka on local-path PVC, single broker: events lost if node disk dies] →
  accepted; events are already best-effort by design (see CLAUDE.md gotcha —
  do not make publishing transactional as part of this change).
- [QEMU-emulated arm64 layer builds could get slow later if a Dockerfile grows
  a real build stage] → revisit D2 (native arm runner) only if CI time hurts.

## Migration Plan

1. Repo-side work (Dockerfile, CI multi-arch, manifests, runbook) merges first —
   it is inert without a cluster.
2. User creates VM + Cloudflare DNS (blocking external steps, clearly marked).
3. On-cluster bring-up follows the runbook order: k3s install → namespace →
   secrets → postgres → kafka → core-api → frontend → ingress + TLS secret.
4. Verify end-to-end at hanatalk.online (M1 Playwright flow + Kafka console
   consumer inside the cluster).
5. Rollback story: `kubectl rollout undo` for app regressions (previous image
   still on GHCR); full-cluster rebuild from runbook for infra mistakes.

## Open Questions

- Is `hanatalk.online` already registered and on a Cloudflare account? (Assumed
  yes per roadmap; if not, registration is a user prerequisite alongside the VM.)
- Oracle home region availability for A1 — if capacity never frees up, fallback
  is an amd64 E2.1.Micro pair, which cannot fit this stack; the realistic
  fallback is waiting/retrying, so surface this early.
