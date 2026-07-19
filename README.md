# HanaTalk

Japanese language-learning platform organized around **JLPT levels (N5–N1)**.
Portfolio project demonstrating polyglot backend engineering across
Kotlin/Spring Boot, Python/FastAPI, and Go, tied together with Kafka and
deployed on Kubernetes.

**Live at [https://hanatalk.online](https://hanatalk.online)** — Cloudflare-proxied, single-node k3s on Oracle Cloud (A1 Flex, aarch64)

---

## Architecture

```
                    +-------------+
                    |   React     |
                    |  Frontend   |
                    +------+------+
                           | HTTPS (via Cloudflare)
                           v
              +------------------------+
              |   Core API (Gateway)   |
              |  Kotlin + Spring Boot  |
              |  - Auth (JWT)          |
              |  - Users & progress    |
              |  - Courses/lessons     |
              |  - PostgreSQL          |
              +-----+----------+-------+
         sync REST    |          |  Kafka events
   (exercise request) |          |  (user.registered,
                       v          |   exercise.completed)
          +--------------------+ |
          |  AI Exercise Svc   | |
          |  Python + FastAPI  | |            v
          |  - LLM failover    | |   +------------------+
          |  - MongoDB         | +-->|  Event Worker    |
          +--------------------+     |  Go              |
                                     |  - Streaks       |
                                     |  - Leaderboard   |
                                     +------------------+
```

## Service status

| Service | Stack | Status |
|---|---|---|
| [core-api](core-api/) | Kotlin + Spring Boot + PostgreSQL | **Working.** JWT auth, courses/lessons, JLPT progress tracking, Kafka publishing, Prometheus metrics + OTel tracing, Flyway V1–V7, controller tests, CI |
| [frontend](frontend/) | React + TypeScript (Vite) | **Working.** Auth, course browsing, lessons, progress; nginx production image |
| [ai-exercise-svc](ai-exercise-svc/) | Python + FastAPI + MongoDB | Planned (Milestone 3) |
| [event-worker](event-worker/) | Go + Kafka consumer | Planned (Milestone 4) |
| [infra](infra/k8s/) (k8s on Oracle) | k3s, Kafka (KRaft), Cloudflare | **Deployed.** Single-node k3s, multi-arch GHCR images, Traefik + Origin CA TLS — see the [runbook](infra/k8s/README.md) |

**Key design decisions:**

- The Core API is the single gateway — the frontend never calls other services directly.
- Exercise generation is **synchronous** (user is waiting). Side effects (streaks,
  leaderboard) are **asynchronous** via Kafka. Realistic split, not "Kafka for everything."
- The AI Exercise Service will use an abstract `LLMProvider` interface with automatic
  failover on rate limits — free-tier LLM APIs make failover a real requirement, not
  a demo.
- Postgres and MongoDB each have a specific reason: relational structure for
  users/auth/progress, variable-shape document storage for generated exercise content.
- Kafka publishing is currently fire-and-forget with error logging
  (`EventPublisher.kt`); a transactional outbox is deliberately deferred — see the
  roadmap discussion of trade-offs.

---

## Core API surface (current)

| Endpoint | Auth | Purpose |
|---|---|---|
| `POST /api/auth/register`, `POST /api/auth/login` | public | JWT issue |
| `GET /api/courses`, `GET /api/courses/{id}` | public | Browse courses, filter by `?jlptLevel=` |
| `GET /api/courses/{id}/lessons`, `.../lessons/{id}` | public | Lesson content |
| `POST/PUT/DELETE` on courses & lessons | JWT | Content CRUD (admin role planned) |
| `POST /api/courses/{c}/lessons/{l}/complete` | JWT | Mark lesson complete (publishes `exercise.completed`) |
| `GET /api/courses/{id}/progress` | JWT | Per-course completion |
| `GET /api/users/me`, `PATCH /api/users/me/level` | JWT | Profile & JLPT level |

---

## Local setup

### Prerequisites

- Docker and Docker Compose
- JDK 21 (only for running Gradle outside Docker)

### Run with Docker Compose

```bash
cd infra
docker compose up --build
```

Starts Postgres, Kafka (KRaft single node), and the Core API at
`http://localhost:8080`. The database is seeded with an N5 starter course via
Flyway, so the API is demoable immediately.

**Health checks:**

```bash
curl http://localhost:8080/api/health        # {"status":"ok"}
curl http://localhost:8080/actuator/health   # {"status":"UP", ...}
```

### Run tests

```bash
cd core-api
./gradlew test
```

Tests use `@WebMvcTest` and run without a database — safe to run locally without
Postgres or Docker.

---

## Roadmap (milestones, not calendar weeks)

| Milestone | Goal |
|---|---|
| **M1 — Vertical slice** ✅ | React frontend: register → log in → browse seeded N5 course → complete lessons → see progress. CORS, JWT in the browser. |
| **M2 — Deployed & public** ✅ | k3s on Oracle Always Free, images pushed to GHCR from CI, Kafka in-cluster, Cloudflare TLS at hanatalk.online. |
| **M3 — AI exercises** | Exercise domain in core-api (MCQ / fill-in-blank, graded in the gateway) + Python FastAPI generation service with LLM provider failover and MongoDB caching. |
| **M4 — Async side effects** | Go event-worker: Kafka consumer group, streaks + leaderboard, read API proxied through the gateway. |
| **M5 — Polish** | Cross-service OTel tracing, Grafana dashboards, admin role for content CRUD, architecture/decisions doc. |

---

## CI/CD

Each service has its own GitHub Actions workflow triggered only when its
directory changes:

- `.github/workflows/core-api.yml` — lint (ktlint), test, build jar; multi-arch
  (amd64+arm64) Docker image pushed to GHCR on `main`.
- `.github/workflows/frontend.yml` — lint (oxlint), build; multi-arch nginx
  image pushed to GHCR on `main`.

Deploys are a `kubectl rollout restart` away — see the
[k8s runbook](infra/k8s/README.md) for the full deploy/rollback procedure.
