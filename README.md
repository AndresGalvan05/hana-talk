# HanaTalk

Language-learning platform for Spanish, English, and Japanese. Portfolio project
demonstrating polyglot backend engineering across Kotlin/Spring Boot, Python/FastAPI,
and Go, tied together with Kafka and deployed on Kubernetes.

**Domain:** hanatalk.online (proxied through Cloudflare, hosted on Oracle Cloud Always Free)

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
                       v          |   exercise.completed,
          +--------------------+ |   streak.updated)
          |  AI Exercise Svc   | |
          |  Python + FastAPI  | |
          |  - Groq/OpenRouter | |            v
          |  - MongoDB         | |   +------------------+
          +--------------------+ +-->|  Event Worker    |
                                      |  Go              |
                                      |  - Notifications |
                                      |  - Streaks       |
                                      |  - Leaderboard   |
                                      +------------------+
```

**Key design decisions:**

- The Core API is the single gateway — the frontend never calls other services directly.
- Exercise generation is **synchronous** (user is waiting). Side effects (streaks,
  notifications, leaderboard) are **asynchronous** via Kafka. Realistic split, not
  "Kafka for everything."
- The AI Exercise Service uses an abstract `LLMProvider` interface: Groq (Llama 3.3)
  primary, OpenRouter fallback #1, Gemini Flash-Lite fallback #2. Automatic failover
  on 429s, with per-request provider logging.
- Postgres and MongoDB each have a specific reason: relational structure for users/auth/
  progress, variable-shape document storage for generated exercise content.

---

## Stack

| Layer | Choice |
|---|---|
| Core API | Kotlin + Spring Boot, PostgreSQL |
| AI Exercise Service | Python + FastAPI, MongoDB |
| Event Worker | Go, Kafka consumer |
| Frontend | React |
| Messaging | Apache Kafka (KRaft mode) |
| Orchestration | Kubernetes (k3s), Oracle Cloud Always Free |
| Edge / Security | Cloudflare (DNS, proxy, free SSL) |

---

## Local Setup

### Prerequisites

- Docker and Docker Compose
- JDK 21 (for running Gradle locally — not needed if you only use Docker)

### Bootstrap (one-time)

The Gradle wrapper JAR is not committed. Generate it once after cloning:

```bash
cd core-api
gradle wrapper --gradle-version 8.13
```

Then commit the generated `gradle/wrapper/gradle-wrapper.jar`:

```bash
git add core-api/gradle/wrapper/gradle-wrapper.jar
git commit -m "chore: add gradle wrapper jar"
```

### Run locally with Docker Compose

```bash
cd infra
docker compose up --build
```

This starts Postgres and the Core API together. The API is available at
`http://localhost:8080`.

**Health checks:**

```bash
curl http://localhost:8080/api/health
# {"status":"ok"}

curl http://localhost:8080/actuator/health
# {"status":"UP", "components": {...}}
```

### Run tests

```bash
cd core-api
./gradlew test
```

Tests use `@WebMvcTest` and run without a database — safe to run locally without
Postgres or Docker.

---

## Phased Timeline

| Weeks | Milestone |
|---|---|
| 1–3 | Core API: auth, course/lesson CRUD, Postgres, tests, Dockerized, CI |
| 3–5 | React frontend wired to Core API |
| 5–7 | Kafka (KRaft) + Go Event Worker |
| 7–9 | Python/FastAPI AI Exercise Service |
| 9–10 | Full k3s deployment, Helm migration (if duplication pain is real), Cloudflare live |

---

## CI/CD

Each service has its own GitHub Actions workflow triggered only when its directory
changes. Current workflows:

- `.github/workflows/core-api.yml` — lint (ktlint), test, build jar, build Docker image

Deploy steps are added once the k3s cluster is live (Phase 4).
