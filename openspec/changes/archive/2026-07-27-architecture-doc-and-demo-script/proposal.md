## Why

This is the last M5 item and the last item on the whole roadmap. Unlike
every prior change, there's no new system behavior to build — the
interview-story value at this point is entirely in how well the *existing*
work (five milestones of real decisions, real bugs, real trade-offs) is
presented to someone who wasn't in the room for any of it. `docs/ROADMAP.md`'s
decision log and `docs/DEVLOG.md` already hold this history, dated and
detailed, but they're a chronological log, not a narrative someone would
read in one sitting before a technical interview. `README.md` has also
drifted stale in the process (M3/M4/M5 roadmap rows unchecked despite
being done; "admin role planned" despite `admin-content-authoring` having
shipped; the AI Exercise Service description still in future tense despite
`provider-failover-chain` having shipped) — the exact kind of staleness
`docs/DEVLOG.md` 2026-07-14 already flagged once before.

## What Changes

- New `docs/ARCHITECTURE.md`: a synthesized, interview-ready architecture
  and trade-offs document — not new design work, a curated narrative built
  from decisions already made and dated across `docs/ROADMAP.md`'s
  decision log and `docs/DEVLOG.md`'s session entries. Covers: the system
  diagram and why each service exists in its language/stack; the
  synchronous-vs-asynchronous boundary (exercise generation blocks the
  user, streaks/leaderboard don't); the outbox-pattern discussion (Kafka
  publishing is fire-and-forget by design, not an oversight — the explicit
  trade-off already made in `EventPublisher.kt`); cross-service ownership
  boundaries (`event-worker` never reads core-api's tables, even sharing
  one Postgres instance); the provider-failover chain's design; the RBAC
  model (role re-derived from the database every request, no JWT claim);
  the observability choice (direct-to-Grafana-Cloud OTLP export, no
  in-cluster agent); and a closing "what I'd do differently at scale"
  section — a genuine, specific answer (not a hedge), since this project
  accumulated enough real trade-offs to have honest answers.
- New `docs/DEMO_SCRIPT.md`: a literal, timed 2-minute live-demo script —
  concrete actions and talking points in sequence, not a vague outline.
  Built to actually be followable in an interview, referencing the real,
  currently-live site and real seeded content.
- `README.md` refresh: fix the staleness found while writing the above
  (roadmap checkmarks, admin-role tense, LLM-failover tense), and link to
  the new `docs/ARCHITECTURE.md`.

## Capabilities

### New Capabilities
- `project-documentation`: the architecture/trade-offs document and demo
  script exist, are accurate against the current state of the system, and
  meet minimum content/format bars (below).

### Modified Capabilities
(none — this changes no system behavior, only documentation)

## Impact

- **docs/**: new `ARCHITECTURE.md`, new `DEMO_SCRIPT.md`.
- **README.md**: staleness fixes, new link.
- **No code changes** — this is the one change in the whole project with
  zero application code touched.
- **Non-goals / cut line**: no diagrams-as-code tooling (a text-based
  diagram, matching the README's existing ASCII architecture diagram
  style, is sufficient); no video recording of the demo (a script to
  follow, not a produced artifact); no changes to any other doc beyond
  README's staleness fixes.
- **Milestone**: M5, final step — completes the roadmap.
