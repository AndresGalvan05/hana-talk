# Tasks — deploy core stack to k3s on Oracle Always Free

Groups 1–4 are repo-side and need no cluster. Group 5 is **user-blocking**
external work. Groups 6–7 need the VM to exist.

## 1. Frontend production image

- [x] 1.1 Write `frontend/Dockerfile`: node:24-alpine build stage (`npm ci`,
      `npm run build` with `VITE_API_URL=""`) → nginx:alpine serving `dist/`
- [x] 1.2 Add nginx config with SPA fallback (`try_files $uri /index.html`)
      and verify a client-side route deep-link works in the local container
- [x] 1.3 Build and run the image locally against compose core-api to confirm
      same-origin `/api` requests (adjust local check via port mapping)

## 2. CI: multi-arch images to GHCR

- [x] 2.1 Convert `core-api.yml` image steps to buildx
      (`platforms: linux/amd64,linux/arm64`, QEMU setup, sha + latest tags)
- [x] 2.2 Extend `frontend.yml` with a buildx build-and-push job mirroring
      core-api's (push only on main, `packages: write` permission)
- [ ] 2.3 After first main build: verify both GHCR manifests list amd64+arm64
      (`docker buildx imagetools inspect`) and mark both packages public

## 3. Kubernetes manifests (`infra/k8s/`)

- [x] 3.1 Rename namespace to `hanatalk`; update existing core-api/postgres
      manifests (namespace, image tags, `imagePullPolicy: Always`)
- [x] 3.2 Convert postgres to a single-replica StatefulSet with
      `volumeClaimTemplates` (local-path SC)
- [x] 3.3 Write kafka StatefulSet + Service: apache/kafka:3.9.1, KRaft combined
      mode, internal listener `kafka:9092`, PVC, stable clusterID across restarts
- [x] 3.4 Update core-api ConfigMap (KAFKA_BOOTSTRAP_SERVERS=kafka:9092,
      `OTEL_SAMPLING_PROBABILITY=0.0`, drop otel-collector endpoint) and add
      actuator-backed readiness/liveness probes with startup headroom for
      JVM + Flyway
- [x] 3.5 Write frontend Deployment + Service (nginx image, HTTP probe)
- [x] 3.6 Write the Ingress: hanatalk.online, `/api` → core-api:8080, `/` →
      frontend:80, TLS secret reference; confirm actuator paths are not routed
- [x] 3.7 Replace secret.yaml placeholders with a template + creation
      commands; confirm nothing secret-valued is committed

## 4. Runbook (`infra/k8s/README.md`)

- [x] 4.1 Rewrite README as the provisioning runbook: VM creation (A1 Flex
      4 OCPU/24GB, capacity-retry note), NSG/security-list + iptables rules
      (only 22/80/443), k3s install, kubeconfig-over-SSH access (6443 never
      public)
- [x] 4.2 Document bring-up order (namespace → secrets → postgres → kafka →
      core-api → frontend → ingress + TLS secret), deploy
      (`rollout restart` + `:latest` trade-off), rollback, and in-cluster
      Kafka console-consumer spot-check
- [x] 4.3 Document Cloudflare setup: proxied DNS, SSL Full (strict), Origin CA
      cert creation and `kubectl create secret tls` step

## 5. External prerequisites (USER — blocking for groups 6–7)

- [ ] 5.1 USER: create Oracle A1 Flex VM (4 OCPU / 24 GB, Ubuntu, aarch64);
      record public IP and SSH key
- [ ] 5.2 USER: confirm `hanatalk.online` is registered and on Cloudflare;
      point proxied A record at the VM IP
- [ ] 5.3 USER: create Cloudflare Origin CA certificate and set SSL mode to
      Full (strict)

## 6. Cluster bring-up (on the VM, following the runbook)

- [ ] 6.1 Apply firewall rules, install k3s, verify Traefik + local-path ready;
      copy kubeconfig and verify `kubectl` from laptop via SSH tunnel
- [ ] 6.2 Create namespace and secrets (`core-api-secret`, TLS secret) per
      runbook
- [ ] 6.3 Apply postgres and kafka; verify both Ready and kafka survives pod
      delete without metadata mismatch
- [ ] 6.4 Apply core-api; verify Flyway migrated + seeded, readiness gates
      traffic, logs free of OTLP errors
- [ ] 6.5 Apply frontend + ingress; verify https://hanatalk.online serves the
      SPA with valid TLS and HTTP redirects to HTTPS

## 7. End-to-end verification & docs

- [ ] 7.1 Full M1 flow on https://hanatalk.online from a fresh browser:
      register → login → N5 course → lesson → mark complete → checkmark +
      progress bar; console free of CORS/mixed-content errors; deep-link a
      client route
- [ ] 7.2 In-cluster Kafka consumer shows `user.registered` /
      `exercise.completed` events from the public flow; confirm API still
      succeeds with kafka pod scaled down (best-effort intact)
- [ ] 7.3 Exercise the deploy loop once: push a trivial frontend change →
      CI → rollout restart → visible live; then `kubectl rollout undo`
- [ ] 7.4 Update docs: ROADMAP M2 status, DEVLOG session entry, root +
      infra/k8s READMEs, CLAUDE.md verification section if commands changed
