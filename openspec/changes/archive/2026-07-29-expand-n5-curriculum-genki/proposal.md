## Why

The roadmap (M1–M5) is complete and the full polyglot stack is live, but the
actual language-learning content it's built to serve is thin: one course,
five lessons, roughly 15 minutes of material. For a "Japanese
language-learning app," the teaching experience itself is the weakest part
of the product right now — every backend capability (grading, LLM-generated
exercises, streaks, admin CRUD) exists to serve content that barely exists.
This proposes deepening the existing N5 course and extending it, using
**Genki I** (the textbook most university/self-study N5–N4 courses are
actually built around) as the structuring reference, rather than inventing
an ad-hoc curriculum.

## What Changes

- Rewrite the five existing lessons (Greetings, Self-introduction, Numbers
  1–10, The AはBです pattern, これ/それ/あれ) with materially more depth:
  more vocabulary per lesson, more example sentences, and grammar notes that
  explain the *why* (particle function, conjugation pattern) rather than
  just stating the pattern — while keeping each lesson's core topic and the
  course's existing position/ordering intact, since `user_lesson_progress`
  rows and any already-completed lessons must remain valid against them.
- Add five new lessons continuing the course through the next block of
  Genki I grammar (roughly Genki I Lessons 3–5's material): existence
  (あります/います) and location words, daily-routine verbs with the
  を/に/で particles and time expressions, and past tense with
  い-adjectives/な-adjectives and likes/dislikes (すき/きらい). Content is
  original writing referencing Genki's topic sequence and grammar points,
  not reproduced textbook text.
- New lessons need **no new exercise-seeding work**: `ai-exercise-svc`
  already generates and caches exercises for any lesson on first request
  (unchanged since M3) — the existing pipeline just gets five more lessons
  to run against, exactly like it already does for lessons 2, 3, and 5.
- **Non-goals / cut line**: Genki I Lessons 6–12 and all of Genki II (N4
  material) are explicitly out of scope for this change — a ten/eleven-lesson
  N5 course is the target, not a full textbook port. No new course entity,
  no N4 course, no audio/pronunciation content, no furigana-toggle or
  reading-level features, no changes to the lesson content's rendering
  (still a plain `<pre>` block — see `docs/ARCHITECTURE.md` for why no
  markdown pipeline exists yet).

## Capabilities

### New Capabilities
- `curriculum-content`: defines what depth/structure an N5 course's lesson
  content must have (no such requirement exists in any current spec — M1's
  original course/lesson browsing feature predates OpenSpec adoption and was
  never captured as a delta spec).

### Modified Capabilities
(none — no API, schema, or behavior changes; this is content only, delivered
through the existing `courses`/`lessons` tables and the existing
`GET /api/courses/{id}/lessons` read path)

## Impact

- **core-api**: one new Flyway migration (`V11__expand_n5_lessons.sql` or
  similar) updating the five existing lesson rows' `content` and inserting
  five new lesson rows at positions 6–10. No Kotlin code changes — content
  only.
- **No changes** to frontend, `ai-exercise-svc`, `event-worker`, or infra.
- **Milestone**: none of M1–M5 (roadmap already complete, per
  `docs/ROADMAP.md`) — this is new post-roadmap content work, not a gap in
  a prior milestone.
