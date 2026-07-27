## Context

The N5 course is entirely Flyway-seeded (`V7__seed_n5_course.sql`): one
course row and five lesson rows with fixed UUIDs, `content` as a single
plain-text column rendered verbatim inside a `<pre>` block on the frontend
(`LessonPage.tsx` — `<pre className="lesson-text">{lesson.content}</pre>`,
no markdown parsing). `V9__seed_exercises.sql` separately seeded four
placeholder MCQ/fill-in-blank exercises tied to lessons 1 and 4 specifically
— lessons 2, 3, and 5 have never had seeded exercises and already get
real exercises from `ai-exercise-svc` generated on first request and cached
in MongoDB (verified live during the 2026-07-27 production rehearsal).
`user_lesson_progress` rows reference lesson IDs directly; any existing
user's completion state must remain valid after this change.

## Goals / Non-Goals

**Goals:**
- Materially deepen the five existing lessons' content without changing
  their identity (id, position, topic) or invalidating progress rows or the
  four already-seeded exercises tied to lessons 1 and 4.
- Add five new lessons continuing the course through Genki I's next block of
  core grammar, positioned immediately after the existing five.
- Keep the whole change to a single new Flyway migration — no code changes.

**Non-Goals:**
- Full Genki I/II coverage (see proposal's cut line).
- Any change to how lesson content is rendered, stored, or fetched.
- Seeding exercises for the five new lessons — the existing on-demand
  generation path already handles this with zero extra work.

## Decisions

**Content authored via a Flyway migration, not the admin CRUD API.**
`admin-content-authoring` (M5) added `POST`/`PUT` course and lesson
endpoints, and using them live against production was considered — it would
double as a real exercise of that feature. Rejected for this change because
Flyway is the existing source of truth for seed content (`V7`, `V9`): every
fresh environment (local compose, CI, a rebuilt cluster) needs this content
present from first boot, which only a migration guarantees. Using the admin
API instead would leave fresh installs with only the original five lessons,
silently diverging from production. The admin API remains the right tool
for *live, ad-hoc* edits after this migration lands — not for baseline seed
content.

**Existing lessons are `UPDATE`d in place, keyed by their existing fixed
UUIDs; new lessons are `INSERT`ed at positions 6–10 with UUIDs continuing
the existing `...000000000{0N}` convention.** This is what preserves
`user_lesson_progress` validity and keeps `V9`'s seeded exercises attached
to the right lessons — there is no scenario where deleting and re-inserting
the original five is preferable.

**Rewritten lesson 1 and lesson 4 content must still contain the exact
phrases `V9`'s seeded exercises depend on**, specifically:
`ありがとうございます` and `すみません` (lesson 1, both exercises quote
these verbatim), and the topic particle `は` plus copula `です` in the
`わたしは がくせい です` pattern (lesson 4, both exercises test exactly
this). Expanding these lessons with more vocabulary and explanation is
fine and intended; removing or renaming the specific taught items the
seeded exercises quiz on is not — that would silently make two
already-correct exercises wrong. Lessons 2, 3, 5, and all five new lessons
have no such constraint, since their exercises are LLM-generated fresh
against whatever content exists at generation time.

**Content style matches the existing plain-text convention** (dash-bulleted
vocabulary, a blank-line-separated grammar note, romaji in parentheses
after each Japanese term) rather than introducing any new formatting
convention — the `<pre>` block has no markdown rendering, so headers/bold
markdown syntax would render as literal asterisks/hashes. Line lengths kept
similar to the existing five lessons (short enough not to require
horizontal scroll on a typical viewport, matching the existing content's
implicit wrapping).

**Genki I chapter mapping for the five new lessons** (original writing
referencing these topics/grammar points, not reproduced textbook text):
| # | Position | Genki I ref. | Topic |
|---|---|---|---|
| 6 | 6 | Lesson 3 | あります/います (existence) + location words |
| 7 | 7 | Lesson 3–4 | Telling time + basic time expressions |
| 8 | 8 | Lesson 4 | Daily-routine verbs (ます-form) + を/に/で particles |
| 9 | 9 | Lesson 5 | Past tense (~ました/~ませんでした) |
| 10 | 10 | Lesson 5 | い-adjectives/な-adjectives + すき/きらい (likes/dislikes) |

## Risks / Trade-offs

- **Content accuracy risk**: this is original Japanese-teaching content
  authored directly, not sourced from a reviewed textbook excerpt. Grammar
  points are well-established (existence verbs, ます-form conjugation, past
  tense, adjective types are standard N5 curriculum, not novel claims), but
  a genuine product would want a fluent-speaker review pass before treating
  this as authoritative → mitigated by keeping every new grammar point to
  textbook-standard patterns (no invented shortcuts or unusual phrasing)
  and cross-checking each example sentence's particle usage before writing
  the migration.
- **Migration must run cleanly against an already-migrated database**
  (production currently sits at `V10`) → mitigated by using `UPDATE ...
  WHERE id = '<fixed-uuid>'` for the five existing lessons (idempotent
  against the exact rows `V7` created) and plain `INSERT` for the five new
  ones, tested locally against a `docker compose` stack that already ran
  `V1`–`V10` before this migration is written, not just a fresh database.
- **New lessons' first-ever exercise-generation call** will hit
  `ai-exercise-svc`'s live LLM provider chain the same way lesson 2/3/5 did
  — acceptable and unchanged behavior, not a new risk, but worth remembering
  during verification: opening all five new lessons in testing will consume
  five real LLM calls (free-tier quota), same consideration as the demo
  script's "don't burn the fresh-lesson moment in rehearsal" note.

## Migration Plan

Single new migration, `V11__expand_n5_lessons.sql`: five `UPDATE lessons SET
content = ... WHERE id = '<existing-uuid>'` statements (content only —
title, position, course_id untouched) followed by five `INSERT INTO
lessons` statements for positions 6–10 with new fixed UUIDs
(`0b4f9a12-2222-4a5e-9d3c-00000000000{6..10}`, continuing `V7`'s pattern).
No rollback migration needed per this project's existing Flyway convention
(no prior migration in this repo has one); reverting would mean a new
forward migration restoring prior content, same as any other schema/data
fix in this codebase's history.

## Open Questions

None — scope, constraints, and content structure are all resolved above.
