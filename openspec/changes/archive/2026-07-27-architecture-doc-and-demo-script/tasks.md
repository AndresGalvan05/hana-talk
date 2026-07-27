## 1. Research pass

- [x] 1.1 Re-read `docs/ROADMAP.md`'s full decision log and `docs/DEVLOG.md`'s
      full session history end to end; list every real trade-off/bug/
      decision worth citing by name, so the doc references specifics
      (dates, file names, exact bugs) rather than generic claims
- [x] 1.2 Audit `README.md` against current code/`docs/ROADMAP.md` for
      staleness (roadmap checkmarks, tense on shipped features, service
      status table) — list every stale claim found

## 2. `docs/ARCHITECTURE.md`

- [x] 2.1 System overview: expanded diagram (Kafka topics and both
      consumer relationships shown explicitly, not just the service boxes)
- [x] 2.2 Why polyglot: per-language/framework rationale
- [x] 2.3 Sync vs. async boundary, with the real reasoning
- [x] 2.4 Outbox-pattern trade-off discussion (fire-and-forget
      `EventPublisher`, `max.block.ms=3000`, why deferred)
- [x] 2.5 Cross-service ownership boundaries (`event-worker`'s own
      schema/projection despite a shared Postgres instance)
- [x] 2.6 Security model (role re-derived per request, no JWT claim, no
      self-service admin escalation)
- [x] 2.7 Observability approach (direct-to-Grafana-Cloud OTLP, no
      in-cluster agent, why)
- [x] 2.8 "What I'd do differently at scale" — specific, grounded in
      this project's own cut lines

## 3. `docs/DEMO_SCRIPT.md`

- [x] 3.1 Draft a timed sequence (~2 minutes) covering: register/login,
      browse the seeded N5 course, complete a lesson via a generated
      exercise, show the resulting streak/leaderboard update, briefly
      mention the architecture (Kafka/polyglot) while it happens
- [x] 3.2 Verify every referenced action against the live production site
      (or local stack if a feature isn't in production) — no scripted step
      that doesn't actually work as described

## 4. README refresh

- [x] 4.1 Fix every staleness item found in task 1.2 (roadmap checkmarks,
      admin-role tense, LLM-failover tense, service status table)
- [x] 4.2 Add a link to `docs/ARCHITECTURE.md` from the README

## 5. Verification

- [x] 5.1 Read `docs/ARCHITECTURE.md` end to end as if encountering the
      project cold — confirm each of the 8 sections lands as a coherent
      "why," not a re-paste of the decision log
- [x] 5.2 Read `docs/DEMO_SCRIPT.md` end to end and confirm the total
      timing is realistic for ~2 minutes
- [x] 5.3 Update `docs/ROADMAP.md` (M5 fully done — completes the
      roadmap) and `docs/DEVLOG.md` (session entry)
