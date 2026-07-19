# Deploy core stack to k3s on Oracle Always Free

**Milestone:** M2 — Deployed & public (see docs/ROADMAP.md).

## Why

M1 delivered a working vertical slice, but it only runs on localhost. A portfolio
project that can't be opened from a link barely exists in an interview: "deployed
and demoable" is the whole point of M2. Deploying now (before exercises) also
means M3/M4 land on real infrastructure instead of accumulating an ever-bigger
"deploy someday" risk.

## What Changes

- **Frontend becomes deployable**: production Dockerfile (static build served by
  nginx), CI extended to push a `ghcr.io/andresgalvan05/hana-talk/frontend` image.
- **Images become multi-arch**: Oracle A1 Flex is aarch64; current CI builds
  amd64-only images that will not run there. Both image workflows switch to
  buildx `linux/amd64,linux/arm64` (jar and static assets are arch-independent,
  so this is cheap).
- **Kafka joins the cluster manifests**: single-node KRaft (`apache/kafka`),
  in-cluster listener only, with a PVC.
- **Existing core-api/postgres manifests get finished and corrected** for the
  real cluster (namespace decision, image tags, probes, seeded Flyway data works
  as-is).
- **Public entry point**: Traefik (bundled with k3s) IngressRoute — same-origin
  routing (`/api` → core-api, everything else → frontend) at `hanatalk.online`,
  TLS via Cloudflare proxy + origin certificate.
- **Provisioning & operations runbook**: documented manual steps for the Oracle
  VM, k3s install, firewall/NSG rules, secret creation (plain k8s Secrets applied
  manually — documented simplification), and deploy/rollback commands.

## Capabilities

### New Capabilities

- `cluster-provisioning`: the Oracle VM + k3s single-node cluster — how it is
  created, secured (firewall/NSG), and what must exist before anything deploys
  (runbook-backed; VM creation itself is a user action).
- `deployment-pipeline`: CI builds, tests, and pushes multi-arch images for
  core-api and frontend to GHCR on merge to main; deploys are a documented
  manual `kubectl` step (no GitOps at this milestone).
- `k8s-runtime`: the in-cluster stack — postgres (PVC), Kafka (single-node
  KRaft, PVC), core-api, frontend — configuration via ConfigMaps, secrets via
  manually-applied k8s Secrets, health probes, resource limits sized for the
  A1 Flex free tier.
- `public-ingress`: Traefik routing at hanatalk.online — `/api` prefix to
  core-api, all other paths to frontend (same origin, so no CORS in prod),
  Cloudflare-proxied DNS and TLS.

### Modified Capabilities

None — `openspec/specs/` is empty; this change introduces the first spec'd
capabilities.

## Impact

- `infra/k8s/**` — existing core-api/postgres manifests revised; new kafka,
  frontend, and ingress manifests; README rewritten from "not started" to a
  real runbook.
- `frontend/` — new `Dockerfile` (+ nginx config); `VITE_API_URL` baked empty
  for same-origin `/api` calls in the production image.
- `.github/workflows/core-api.yml`, `frontend.yml` — buildx multi-arch; frontend
  workflow gains a GHCR push job.
- `core-api` — no code changes expected; already fully env-driven
  (`DB_*`, `KAFKA_BOOTSTRAP_SERVERS`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`).
- Docs — ROADMAP status, DEVLOG entry, root/infra READMEs.
- **Needs from user (blocking, sequenced in tasks):** create the Oracle A1 Flex
  VM (4 OCPU / 24 GB recommended), own/point the `hanatalk.online` domain at
  Cloudflare.

## Non-goals / cut line

- No GitOps (ArgoCD/Flux), no Helm — raw manifests until a second environment
  makes duplication hurt (per infra/k8s README).
- No autoscaling, no multi-node, no dev/prod namespace split yet — one node,
  one namespace.
- No in-cluster observability stack — OTel sampling set to 0 / endpoint unset
  until M5.
- No cert-manager/Let's Encrypt automation — Cloudflare origin certificate as a
  manually-applied TLS secret is the documented simplification.
- No external secret management (Vault, SOPS) — plain k8s Secrets, documented.
- ai-exercise-svc and event-worker manifests wait for M3/M4.
