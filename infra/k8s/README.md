# infra/k8s — provisioning & operations runbook

Raw Kubernetes manifests for HanaTalk on single-node k3s (Oracle Cloud Always
Free, A1 Flex). This README is the runbook: a dead VM can be rebuilt from
scratch by following it top to bottom (~1 hour). Raw manifests by design —
Helm arrives only when a second environment makes duplication hurt.

Everything runs in the single `hanatalk` namespace (one cluster, one
environment; a dev/prod split would be empty ceremony at this scale).

## 0. Prerequisites (manual user actions, outside this repo)

1. **Oracle A1 Flex VM** — shape `VM.Standard.A1.Flex` (aarch64), boot volume
   ≥ 50 GB. As-built: **4 OCPU / 24 GB on Oracle Linux 9** (a 1 GB Micro
   cannot fit this stack). Record the public IP and keep the SSH private key
   safe. Default SSH user on Oracle Linux is `opc`.
   *Free-tier note:* Oracle halved the Always Free A1 allowance to
   **2 OCPU / 12 GB** on 2026-06-15 — the stack fits in 2/12 too; watch Cost
   Analysis and set a ~$1 budget alert if running larger.
   *Capacity note:* "Out of capacity" errors on A1 creation are normal and can
   persist for days — retry at different hours and across availability
   domains (scripted retries and, on PAYG accounts, other regions improve the
   odds). This blocks everything below; start early.
2. **Domain on Cloudflare** — `hanatalk.online` registered and active on a
   Cloudflare account.
3. **Cloudflare Origin CA certificate** — dashboard → SSL/TLS → Origin Server
   → Create Certificate (hostnames: `hanatalk.online`, `*.hanatalk.online`,
   15-year validity). Save the cert and private key; they become the
   `hanatalk-origin-tls` secret in step 5.
4. **GHCR packages public** — `hana-talk/core-api` and `hana-talk/frontend`
   packages set to public visibility (GitHub → package → settings) so the
   cluster pulls without `imagePullSecrets`. Images are multi-arch; the
   arm64 variant is selected automatically on the A1 node.

*Idle-reclamation note:* Oracle may reclaim Always Free instances it deems
idle. The running stack (JVM + Kafka) generates steady baseline load, which
helps; if it happens anyway, this runbook is the recovery plan (user/progress
data is demo-grade; Flyway re-seeds course content).

## 1. Oracle network (VCN)

In the VCN's default security list (or an NSG attached to the VM), allow
ingress TCP from `0.0.0.0/0` to ports **80** and **443** only. Port 22 stays
open for SSH (restrict the source to your own IP if it is stable).
**Never open 6443** — cluster administration goes over SSH (step 4).

## 2. Host firewall (Oracle Linux 9)

Oracle Linux ships firewalld enabled; k3s's own docs recommend disabling it on
RHEL-family systems (it conflicts with the pod/service network rules for
`10.42.0.0/16` / `10.43.0.0/16`). The VCN security list from step 1 remains
the single enforcement point (22/80/443 only):

```bash
sudo systemctl disable --now firewalld
```

SELinux stays **enforcing** — the k3s installer detects RHEL-family and
installs the `k3s-selinux` policy automatically.

(If rebuilding on Ubuntu instead: its Oracle images ship restrictive iptables
rules — `sudo iptables -I INPUT 5 -p tcp --dport 80 -j ACCEPT`, same for 443,
then `sudo netfilter-persistent save`.)

Verify from the outside: `nmap <vm-ip>` should show only 22, 80, 443 (80/443
refuse connections until Traefik is up — that's fine).

## 3. Install k3s

```bash
curl -sfL https://get.k3s.io | sudo sh -
sudo /usr/local/bin/k3s kubectl get nodes            # Ready
sudo /usr/local/bin/k3s kubectl -n kube-system get pods   # traefik + local-path-provisioner Running
```

(Full path because OL9's sudo `secure_path` omits `/usr/local/bin`.)

k3s bundles everything we rely on: Traefik (ingress) and the `local-path`
default StorageClass (PVCs).

## 4. kubectl from your laptop (no public 6443)

```bash
ssh opc@<vm-ip> "sudo cat /etc/rancher/k3s/k3s.yaml" > ~/.kube/hanatalk.yaml
# Leave server: https://127.0.0.1:6443 as-is — we tunnel to it:
ssh -fN -L 6443:127.0.0.1:6443 opc@<vm-ip>
KUBECONFIG=~/.kube/hanatalk.yaml kubectl get nodes
```

## 5. Namespace and secrets

```bash
kubectl apply -f namespace.yaml

# App + DB credentials (postgres and core-api read the same secret,
# so they can never disagree):
kubectl create secret generic core-api-secret -n hanatalk \
  --from-literal=DB_USERNAME=hanatalk \
  --from-literal=DB_PASSWORD="$(openssl rand -base64 24)" \
  --from-literal=JWT_SECRET="$(openssl rand -base64 48)"

# Cloudflare Origin CA cert from prerequisite 0.3:
kubectl create secret tls hanatalk-origin-tls -n hanatalk \
  --cert=origin-cert.pem --key=origin-key.pem
```

Plain manually-applied Secrets are a documented simplification (no Vault/SOPS
at this scale). Nothing secret-valued is committed — `core-api/secret.yaml.example`
is a template, and `kubectl apply -f <dir>` skips `.example` files.

## 6. Bring-up (in order)

```bash
kubectl apply -f postgres/
kubectl apply -f kafka/
kubectl rollout status -n hanatalk statefulset/postgres statefulset/kafka

kubectl apply -f core-api/          # skips secret.yaml.example
kubectl rollout status -n hanatalk deployment/core-api
# First start runs Flyway migrations incl. the V7 course/lesson seed.
# Logs should be free of OTLP export errors (sampling is 0.0 until M5).

kubectl apply -f frontend/
kubectl apply -f ingress/
```

## 7. Cloudflare DNS + TLS

1. DNS: `A` record `hanatalk.online` → VM public IP, **proxied** (orange cloud).
   *Gotcha:* if the domain came from another registrar, Cloudflare's zone
   import may have copied old records (e.g. GoDaddy Website Builder) — delete
   them, or the proxy keeps serving the old origin while your record sits idle.
2. SSL/TLS mode: **Full (strict)** — edge TLS from Cloudflare's public cert,
   origin leg encrypted against the Origin CA cert Traefik serves.
3. Turn on **Always Use HTTPS** (SSL/TLS → Edge Certificates) — HTTP→HTTPS
   redirect happens at the edge; clients never reach the origin over plain HTTP.

Then verify: `https://hanatalk.online` loads the app; register → course →
lesson → complete works; a deep link like `/courses/<id>` refreshes correctly;
browser console shows no CORS or mixed-content errors (API calls are
same-origin `/api/...` by design).

## 8. Deploying a new version

CI pushes multi-arch images to GHCR on every merge to main. To ship:

```bash
kubectl -n hanatalk rollout restart deployment/core-api    # or deployment/frontend
kubectl -n hanatalk rollout status  deployment/core-api
```

**Trade-off (deliberate):** deploys track `:latest` and are not reproducible —
you get whatever main last built. Escape hatch when that matters:
`kubectl -n hanatalk set image deployment/core-api core-api=ghcr.io/andresgalvan05/hana-talk/core-api:<commit-sha>`.
GitOps is the named next step if this ever bites for real.

**Rollback:** `set image` to a known-good sha (every main commit is tagged on
GHCR) — verified working live. Note `rollout undo` alone is a **no-op** here:
the previous revision also says `:latest` + `imagePullPolicy: Always`, so the
new pod re-pulls the same image you're trying to back out of.

## 9. Kafka spot-check

Events are best-effort by design (never make publishing transactional without
the outbox discussion — see repo README). To watch them:

```bash
kubectl -n hanatalk exec -it kafka-0 -- \
  /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic exercise.completed --from-beginning --timeout-ms 5000
```

(`user.registered` for registrations.) Kafka is cluster-internal only
(`kafka:9092`); there is deliberately no host/external listener.

## Layout

```
k8s/
├── namespace.yaml        # hanatalk
├── postgres/             # StatefulSet + Service (PVC via local-path)
├── kafka/                # StatefulSet + Service, single-node KRaft, pinned CLUSTER_ID
├── core-api/             # Deployment + Service + ConfigMap (+ secret template)
├── frontend/             # Deployment + Service (nginx serving the SPA)
└── ingress/              # Traefik: /api -> core-api, / -> frontend, origin TLS
```

Planned later: `ai-exercise-svc/` + `mongodb/` (M3), `event-worker/` (M4),
observability re-enabled via Grafana Cloud (M5).
