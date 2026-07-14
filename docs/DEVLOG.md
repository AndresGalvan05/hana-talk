# DEVLOG

Newest first. Every working session gets an entry: what shipped, what broke,
and root causes — so no lesson has to be relearned.

## 2026-07-14 — M1 completed; SDD evaluation

**Shipped**
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
