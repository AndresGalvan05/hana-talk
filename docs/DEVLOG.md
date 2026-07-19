# DEVLOG

Newest first. Every working session gets an entry: what shipped, what broke,
and root causes — so no lesson has to be relearned.

## 2026-07-19 — M2 completed: live at https://hanatalk.online

**Shipped**
- Oracle A1 Flex VM finally provisioned (4 OCPU/24 GB aarch64, sa-saopaulo-1)
  after ~4 days of "Out of capacity" retries — with **Oracle Linux 9**, not the
  planned Ubuntu. Kept OL9 rather than risk the capacity slot on a recreate;
  adaptations: `opc` user, `dnf`, firewalld disabled (k3s docs recommend it on
  RHEL-family; the OCI VCN security list is the single firewall, 22/80/443),
  SELinux stays enforcing (`k3s-selinux` auto-installed).
- Single-node k3s v1.36.2 up; kubectl from laptop via SSH tunnel (6443 never
  public). Bring-up per runbook: namespace → secrets (openssl-generated +
  Origin CA TLS) → postgres/kafka StatefulSets → core-api (7 Flyway migrations
  incl. seed) → frontend + ingress. First pod-ready ~30 s each on arm64.
- Kafka restart check passed: pod delete → clean rejoin with pinned CLUSTER_ID.
- GHCR packages made public after discussing public-vs-private trade-off
  (images hold only compiled artifacts; repo is public; avoids PAT-based
  imagePullSecret rotation). Anonymous manifests verified: amd64+arm64.
- Full E2E on the live site via Chrome: register → N5 course → lesson →
  mark complete → checkmark + 20% progress bar; deep links OK; console clean.
  `user.registered` + `exercise.completed` consumed in-cluster from the
  public flow. Best-effort intact: with kafka scaled to 0, login + completion
  still 2xx (~3 s `max.block.ms` penalty).
- Deploy loop rehearsed end-to-end: meta-description commit → CI (52 s) →
  `rollout restart` → live; rollback via **sha-pin escape hatch**
  (`kubectl set image ...:<sha>`) → verified reverted → roll-forward. Plain
  `rollout undo` is a no-op with `:latest` + `Always` — as documented.

**Errors & lessons**
- *GoDaddy ghost records:* hanatalk.online initially served a GoDaddy Website
  Builder 404 through Cloudflare — the zone import had copied old
  builder records; the VM A record wasn't the one being served. Lesson: after
  moving a domain to Cloudflare, audit **all** imported DNS records, not just
  add your own.
- *VCN blocks by default:* 80/443 timed out publicly while everything worked
  in-cluster (verified via `curl -H "Host: ..." https://localhost/` on the VM
  — a useful bisect: proves manifests/TLS/ingress before touching cloud
  firewalls). Ingress rules for 80/443 had to be added in the console.
- *Oracle free-tier rug-pull:* Always Free A1 quietly halved to 2 OCPU/12 GB
  on 2026-06-15, and our 4/24 VM was created **after** the cut — whether it
  stays free is unclear (reports contradict). User watching Cost Analysis with
  a budget alert; the stack fits in 2/12 if a downsize is ever forced.
- *OL9 sudo secure_path* omits `/usr/local/bin` — `sudo k3s` fails; use
  `sudo /usr/local/bin/k3s`.
- *`kubectl wait --for=delete` + StatefulSet recreate race:* after
  `delete pod kafka-0`, waiting for ready can hit the NotFound window while
  the controller recreates it; poll for existence first.

## 2026-07-14 — M1 completed; SDD evaluation; OpenSpec adopted

**Shipped**
- OpenSpec initialized (`openspec init --tools claude`): `openspec/config.yaml`
  with project context + proposal rules, `/opsx:*` slash commands and skills
  under `.claude/`. `.claude/settings.local.json` (per-machine permission
  grants) gitignored. Note: `npm install -g` fails with EACCES on this machine
  (root-owned global prefix) — run the CLI via `npx -y @fission-ai/openspec@latest`.
  This OpenSpec version has no `/opsx:onboard`; baseline specs will accrete as
  changes are archived from M2 onward.
- `completedLessonIds` added to `GET /api/courses/{id}/progress`; course page
  shows per-lesson checkmarks; lesson page pre-marks already-completed lessons.
- Frontend CI workflow (`.github/workflows/frontend.yml`): npm ci → oxlint → build.
- User verified the full browser flow manually; Playwright MCP installed for
  automated browser verification in future sessions.

**Errors & lessons**
- *Stale container shadowing:* verification hit port 8080 and got the **old**
  API — the compose `core-api` container from a previous session was still
  running; the freshly started jar had silently failed with "Port 8080 was
  already in use" while curl kept getting answers (old shape, right counts —
  easy to misread as a code bug). Lesson: `docker ps` before any local-jar
  verification; now in CLAUDE.md gotchas.
- Machine had no git identity configured; set repo-local user.name/email
  matching prior commit authorship.

**SDD tooling evaluation (for M1.5 proposal)**
- Candidates: GitHub Spec-Kit, OpenSpec, others (Kiro, GSD).
- **Recommendation: OpenSpec** — brownfield-first (delta specs against existing
  behavior, exactly our situation), lightweight (propose → apply → archive; no
  constitution/branch-per-feature ceremony), no API keys or MCP dependency,
  most actively maintained SDD framework (~52k stars, mid-2026), native Claude
  Code slash commands (`/opsx:propose`, `/opsx:apply`, `/opsx:archive`).
- Spec-Kit rejected for now: greenfield-oriented, heavier process designed for
  teams; would tax a solo part-time dev without improving the interview story.
- Folder impact is small (a root `openspec/` dir with `changes/` + archived
  specs) — **not** the big restructure originally feared.

## 2026-07-13 — Planning + M1 execution

**Shipped**
- Milestone roadmap approved (see ROADMAP.md). READMEs rewritten to match the
  Japanese-only/JLPT reality (they still described the multilingual pivot-era
  plan). V7 seed migration (N5 course, 5 lessons, fixed UUIDs). CORS
  (env-configurable). React/Vite/TS frontend: auth flow, course browsing with
  JLPT filter, lesson view, mark-complete, progress bar. CI pushes core-api
  image to GHCR on main.

**Errors & lessons**
- *Kafka outage broke registration:* `KafkaTemplate.send()` throws
  **synchronously** when the broker is unreachable (metadata timeout) — the
  `whenComplete` callback only sees async failures. Fixed: try/catch in
  `EventPublisher.send` + `max.block.ms=3000` (default 60s stalled requests for
  a minute). Design intent restored: events are best-effort side effects.
- *5xx masquerading as 401:* Spring Security intercepted the `/error` dispatch
  (not permitAll), so every server error reached clients as an empty 401 —
  which hid the Kafka bug above. `/error` is now permitted.
- *`bitnami/kafka` image gone:* Bitnami withdrew free Docker Hub images (2025);
  `docker compose up` failed for fresh clones. Switched to `apache/kafka:3.9.1`
  with dual listeners (containers `kafka:29092`, host `localhost:9092`).
- *Machine setup:* no Node.js existed (frontend built via a scratchpad Node 24
  tarball; user has since installed system Node). `gradlew` is not executable —
  use `sh gradlew`.

**Known API gap fixed next session:** progress endpoint had no per-lesson data.
