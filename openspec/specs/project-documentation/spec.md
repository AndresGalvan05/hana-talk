# project-documentation

## Purpose

The project's architecture, trade-offs, and demo materials are documented
accurately enough for someone unfamiliar with the project to understand
the real decisions made and to run a live demo against the real system,
with no stale claims about planned-vs-shipped state anywhere in the
top-level docs.

## Requirements

### Requirement: An architecture and trade-offs document exists and reflects the real system
The repository SHALL include a `docs/ARCHITECTURE.md` document that
describes the system's actual architecture and covers, at minimum: the
sync/async boundary, the outbox-pattern trade-off, cross-service ownership
boundaries, the security model, and the observability approach — each
grounded in a decision this project actually made, not a generic
discussion.

#### Scenario: Reader unfamiliar with the project reads the doc
- **WHEN** someone with no prior context reads `docs/ARCHITECTURE.md`
- **THEN** they can explain why exercise generation is synchronous while
  streaks/leaderboard are event-driven, and why Kafka publishing is
  fire-and-forget rather than a transactional outbox

#### Scenario: Doc matches current system state
- **WHEN** `docs/ARCHITECTURE.md`'s description of any service or data
  flow is checked against the current codebase
- **THEN** it matches — no stale claims about planned-but-unshipped or
  shipped-but-undocumented behavior

### Requirement: A timed demo script exists and is runnable as written
The repository SHALL include a `docs/DEMO_SCRIPT.md` with a sequence of
timed steps totaling approximately two minutes, each step naming a
concrete action (a URL, a button, seeded data) and what to say while doing
it.

#### Scenario: Script is followed literally
- **WHEN** someone follows `docs/DEMO_SCRIPT.md` step by step against the
  live production site
- **THEN** every referenced action (page, seeded course/lesson, feature)
  exists and works as described, and the total sequence fits within
  roughly two minutes

### Requirement: README reflects the current state of the project
`README.md` SHALL NOT contain claims that are stale relative to the
project's actual current state (milestone completion, feature status,
planned-vs-shipped language).

#### Scenario: Roadmap table checked against ROADMAP.md
- **WHEN** `README.md`'s roadmap summary table is compared against
  `docs/ROADMAP.md`'s status
- **THEN** completed milestones are marked complete and no shipped
  feature is described as merely planned
