# CLAUDE.md — operating guide for AI sessions

HanaTalk: Japanese language-learning app (JLPT N5–N1), built as a **portfolio
project** for backend/full-stack interviews. Solo dev, part-time, free-tier
hosting. Judge every suggestion by: *stronger interview story + actually
finishable*. "Deployed and demoable" beats "impressive but broken."

## Spec-driven workflow (OpenSpec) — adopted before M2

From M2 onward, every milestone chunk goes through OpenSpec:
`/opsx:propose "<change>"` (proposal + design + tasks under `openspec/changes/`)
→ review → `/opsx:apply` → `/opsx:archive` (folds delta specs into the spec
base). Explore ideas first with `/opsx:explore`. Project context and proposal
rules live in `openspec/config.yaml`; slash commands in `.claude/commands/opsx/`.
The CLI runs via `npx -y @fission-ai/openspec@latest` (no global install — that
hits permission errors on this machine). Keep ROADMAP/DEVLOG as the why/when;
specs are the what.

## Where things are documented

- `docs/ROADMAP.md` — milestone plan (M1–M5), status, cut lines, decision log. **Read this first each session.**
- `docs/DEVLOG.md` — dated history: what was done, bugs found and their root causes. Append an entry every working session.
- `README.md` + per-service READMEs — public-facing; keep in sync when architecture or status changes.
- The stack (Kotlin/Spring, Python/FastAPI, Go, React, Kafka, k3s) is **fixed by design** — plan sequencing and scope, never alternative technologies.

## Commands

```bash
# core-api (from core-api/) — gradlew is not executable, use `sh gradlew`
sh gradlew ktlintCheck test bootJar --no-daemon

# frontend (from frontend/)
npm run dev      # dev server on :5173, expects API on :8080
npm run lint     # oxlint
npm run build    # tsc -b && vite build

# full local stack (from infra/)
docker compose up -d --build     # postgres :5432, kafka :9092, core-api :8080
docker compose down -v           # reset: wipes DB, next start re-seeds via Flyway

# production cluster (k3s on Oracle VM, host alias `oci` in ~/.ssh/config)
ssh -fN -L 6443:127.0.0.1:6443 oci                  # tunnel (6443 is not public)
KUBECONFIG=~/.kube/hanatalk.yaml kubectl -n hanatalk get pods
# deploy: rollout restart; rollback: set image to a sha tag (NOT rollout undo —
# no-op with :latest). Full runbook: infra/k8s/README.md
```

Production is live at https://hanatalk.online (M2, 2026-07-19).

## Verifying changes end-to-end

1. `docker compose up -d --build` (in `infra/`), `npm run dev` (in `frontend/`).
2. Use the **Playwright MCP server** (installed) to drive http://localhost:5173:
   register a fresh user → course list → open "JLPT N5: First Steps in Japanese"
   → open a lesson → Mark as complete → back to course → checkmark + progress bar.
3. Seeded fixture data (Flyway `V7`, expanded to 10 lessons by `V11`): course
   `0b4f9a12-1111-4a5e-9d3c-000000000001`, lessons
   `0b4f9a12-2222-4a5e-9d3c-00000000000{1..9}` +
   `0b4f9a12-2222-4a5e-9d3c-000000000010`. No seeded users — register one.
4. Kafka spot-check:
   `docker exec infra-kafka-1 /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic exercise.completed --from-beginning --timeout-ms 5000`

## Gotchas that have already cost time (details in docs/DEVLOG.md)

- **Port 8080**: the compose `core-api` container often stays up and shadows a
  locally-run jar — Spring logs "Port 8080 was already in use" but curl still
  answers (from the old container, with stale behavior). Check `docker ps` first.
- **Kafka publishing is deliberately best-effort**: `EventPublisher` catches
  synchronous send failures and `max.block.ms=3000` caps blocking. Don't "fix"
  requests failing when Kafka is down by making publishing transactional without
  discussing the outbox trade-off (see README design decisions).
- **`/error` must stay permitAll** in `SecurityConfig` — otherwise every 5xx
  surfaces as an empty 401.
- **Bitnami Docker images no longer exist** on Docker Hub; Kafka uses
  `apache/kafka` with dual listeners (containers: `kafka:29092`, host: `localhost:9092`).
- Tests are `@WebMvcTest` + `@Import(SecurityConfig::class)` — a new required
  property in `SecurityConfig` must have a default in `application.yml` or every
  controller test breaks.
- Conventional commits, no scopes (`feat:`, `fix:`, `ci:`, `docs:`, `chore:`).
  Git identity is repo-local (`git config user.name/email` already set).
