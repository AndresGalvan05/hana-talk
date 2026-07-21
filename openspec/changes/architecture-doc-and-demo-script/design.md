## Context

Every prior change in this project produced code plus a DEVLOG entry plus
a decision-log line. Across M1–M5 that's added up to a genuinely rich,
specific decision history — real bugs with root causes (the OTLP
endpoint-path convention mismatch, hit twice, in two different contexts;
the `RestClient.builder()` vs injected `RestClient.Builder` bug; Kafka
consumer-group rebalance churn from an ungracefully-killed `go run`
process), real trade-offs made under real constraints (Oracle free-tier
capacity, LLM key procurement timing, Grafana Cloud account setup), and
real architectural boundaries deliberately drawn (core-api as the only
public gateway; `event-worker` never reading core-api's tables even
though they share one Postgres instance; grading staying synchronous
while streaks/leaderboard stay async). This document's job is to make that
legible to someone encountering the project cold, not to invent new
narrative.

## Goals / Non-Goals

**Goals:**
- `docs/ARCHITECTURE.md` reads as a coherent narrative, not a re-paste of
  the decision log — organized by topic (data flow, sync/async boundary,
  ownership boundaries, observability, security), each backed by the real
  decision(s) that produced it.
- Every trade-off discussed is one this project actually made, with a
  specific "why," not a generic textbook trade-off list.
- `docs/DEMO_SCRIPT.md` is concrete enough to actually run from — specific
  URLs, specific seeded data, specific timings — not "show the app."
- `README.md`'s staleness is fixed as a byproduct of writing the
  architecture doc (the same audit surfaces both).

**Non-Goals:**
- No new architecture decisions — if writing this surfaces an actual
  inconsistency in the system (not just doc staleness), that's a signal to
  pause and flag it, not to quietly redesign something.
- No tooling investment (diagram generators, doc-site generators) — plain
  Markdown, matching every other doc in `docs/`.

## Decisions

**Structure `ARCHITECTURE.md` around questions an interviewer would
actually ask, not around chronology.** A chronological retelling would
just be a copy of the decision log, which already exists. Sections:
1. System overview (the diagram, expanded from README's, showing Kafka
   topics and the two consumer relationships explicitly).
2. Why polyglot: what each language/framework choice bought (Kotlin/Spring
   for the gateway's maturity and JPA; Python/FastAPI for LLM SDK
   ecosystem access; Go for a deliberately idiomatic, lightweight Kafka
   consumer — not "resume-driven," each pick is defensible on its own
   terms).
3. Sync vs. async: exercise generation is on the request path (the user
   is waiting) and gets a real timeout and a real failover chain; streaks/
   leaderboard are event-driven because nothing is waiting on them.
4. The outbox trade-off: `EventPublisher` is fire-and-forget with
   `max.block.ms=3000` and logged failures, not a transactional outbox —
   stated as a deliberate, load-bearing scope cut (a real interview
   question: "what happens if Kafka is down when a user registers?" has a
   real, considered answer already, not a gap).
5. Ownership boundaries under one shared Postgres: `event-worker` never
   queries core-api's tables, maintaining its own projection instead —
   the trade-off (eventual consistency on the leaderboard's username) is
   explicit, not accidental.
6. Security: role re-derived from the database every request, no JWT
   claim, no self-service admin escalation.
7. Observability: direct-to-Grafana-Cloud OTLP export chosen specifically
   to avoid any in-cluster agent, on a resource-constrained free-tier
   node.
8. What I'd do differently at scale: a real answer (see below), not a
   deflection.

**"What I'd do differently at scale" gets specific, real answers, not
hedges.** Candidates already implied by this project's own trade-offs:
a transactional outbox once event delivery guarantees actually matter;
a real multi-node Postgres (or split databases per service) once
`event-worker`'s shared-instance-but-separate-schema arrangement stops
being "good enough"; provider-failover with circuit breakers/health
tracking instead of "try each once, every time," once traffic volume makes
that worth the complexity. Each is something this project explicitly cut
for portfolio scope (visible in the decision log), not a hypothetical.

**`DEMO_SCRIPT.md` is a timed script, structured as a table or numbered
sequence with explicit second-marks, not prose.** Format: time range →
action → what to say. References real seeded content (the N5 course,
its lesson IDs) and the real production site, so it's rehearsable exactly
as written.

## Risks / Trade-offs

- [A synthesized narrative can drift from the decision log's exact
  wording over time as future work continues] → acceptable; this doc is a
  snapshot, and `docs/DEVLOG.md`/`docs/ROADMAP.md` remain the
  authoritative, continuously-updated sources per `CLAUDE.md`.
- [Writing this surfaced real README staleness, which is scope creep
  beyond "write two new docs"] → fixed anyway, since shipping a new
  architecture doc that contradicts an unfixed, stale README right next to
  it would undermine the credibility this document exists to build.

## Migration Plan

None — pure documentation addition, no code or infrastructure changes.
