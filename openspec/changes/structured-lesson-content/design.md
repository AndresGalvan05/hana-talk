## Context

`Lesson.content: String` (`core-api/.../domain/lesson/Lesson.kt`) is rendered
verbatim by `frontend/src/pages/LessonPage.tsx` as
`<pre className="lesson-text">{lesson.content}</pre>` — no structure, no
markdown. `ai-exercise-svc/app/generation.py`'s `_PROMPT_TEMPLATE` takes that
same flat string and is hardcoded to produce "exactly one MCQ and exactly
one fill-in-the-blank" (`GenerationResult._check_type_coverage` enforces
this). `Exercise.optionsJson: String?` (`core-api/.../domain/exercise/Exercise.kt`)
already establishes the precedent this design reuses: a JSON-string column,
parsed at the DTO layer via `ObjectMapper`, for content whose shape doesn't
need SQL-level querying.

## Goals / Non-Goals

**Goals:**
- Give lessons real structure: vocabulary, multiple grammar points (each
  with explanation + examples), a dialogue, a culture note.
- Make vocabulary independently queryable (future spaced-repetition slice
  needs per-item, per-user tracking — impossible against buried JSON).
- Get `ai-exercise-svc` generating more than a fixed 2 exercises, scaled to
  how much the lesson actually teaches.
- Replace the 10 current lessons with 5 chapter-depth ones, content
  originally written (see proposal's copyright constraint), referencing
  Genki I Lessons 1-5's topic scope only.

**Non-Goals:**
- New exercise types (translation, sentence-ordering) — next slice.
- AI conversation practice, flashcard review, audio — later slices.
- Preserving the current 10 lesson rows' IDs/content — this is a genuine
  replacement, not an edit (see Decisions).

## Decisions

**JSON string column (`contentJson`), not JSONB or normalized tables, for
grammar points / dialogue / culture note.** Matches `Exercise.optionsJson`'s
existing precedent exactly, keeps Hibernate mapping trivial (`String`
column, no new Postgres type or Hibernate JSON converter dependency), and
this content has no query need (never filtered/joined by grammar-point
content) — a document-shaped value stored as a document is the right level
of engineering, not under- or over-built.

**Vocabulary is the one exception: a real `vocabulary_items` table**, FK'd
to `lesson_id`, not part of `contentJson`. The deciding factor is a
capability *this* slice doesn't build but must not block: per-user spaced
repetition needs `WHERE next_review_at <= now()` style queries against
individual vocabulary items, which is not expressible against a JSON blob
without either denormalizing later (a breaking migration) or building it
right the first time. Building it right the first time is cheap now (one
small table) and expensive to retrofit later.

**5 lessons, mapped 1:1 to Genki I Lessons 1-5's topic scope**:
| # | Genki I ref. | Topic |
|---|---|---|
| 1 | Lesson 1 | New Friends — self-intro, X は Y です, questions, Noun の Noun |
| 2 | Lesson 2 | Shopping — これ/それ/あれ/どれ, この/その/あの/どの, ここ/そこ/あそこ/どこ, も |
| 3 | Lesson 3 | Making a Date — verb conjugation (ます-form), particles, time, word order |
| 4 | Lesson 4 | The First Date — あります/います, past tense (です and verbs), と |
| 5 | Lesson 5 | Trip to Okinawa — adjectives (present/past/noun-modifying), すき/きらい, counting |

Each lesson gets 6-8 grammar points (matching real chapter density), a
vocab list (12-20 items), an original dialogue (2 invented characters, not
Genki's Mary/cast — avoids anything that could read as reusing their
specific narrative device), and a culture note. All original writing;
Genki referenced only to check topic scope and grammar accuracy against the
already-purchased books, never transcribed.

**Old lesson rows are replaced outright, not edited or merged** — `V12`
deletes the 10 existing `lessons` rows (and their `user_lesson_progress`
rows cascade) and inserts 5 new ones with new IDs. Rejected the alternative
(map old lesson IDs onto new consolidated ones to preserve progress)
because there's no real end-user data to preserve: three accounts, all
test/demo accounts from this project's own verification sessions, 1-2
completions each. Optimizing for zero real users' data isn't worth the
complexity of a 10-into-5 ID remapping.

**`V9`'s seeded exercises (lessons 1 and 4) are dropped, not migrated.**
They existed only because `ai-exercise-svc` didn't exist yet at M3 slice 1.
Every lesson now generates real exercises on demand — proven live in
production since 2026-07-27. No lesson needs a seeded fallback anymore.

**`ai-exercise-svc` generation input changes from flat `content: str` to
`grammar_points: list[GrammarPointInput]`** (title + explanation per point,
no examples — the LLM writes its own example-grounded questions, it doesn't
need the lesson's own examples fed back to it). `GenerateRequest` drops
`content` entirely.

**Exercise-count validation becomes a floor, not an exact match.**
`GenerationResult._check_type_coverage` (renamed `_check_minimum_variety`)
now requires: at least one `MCQ`, at least one `FILL_IN_BLANK`, and at least
4 exercises total (`MIN_EXERCISES = 4`, a module constant) — self-contained
in the Pydantic model, no cross-model wiring to the request's grammar-point
count needed. The prompt asks for "one exercise per grammar point, two for
points covering multiple distinct rules" as guidance; the floor just
catches a response that's clearly too thin, the same role the old exact-2
check played, just not pinned to a fixed number anymore.

## Risks / Trade-offs

- **Content accuracy, same risk `expand-n5-curriculum-genki` already
  accepted**: original-authored grammar content, not professionally
  reviewed. Mitigated the same way — every point is standard N5 curriculum
  (nothing invented), cross-checked against the reference books for
  accuracy before writing, not copied from them.
- **5 lessons × more LLM calls during verification** than `V11`'s
  10-lessons-with-2-exercises-each did — each lesson's first request now
  generates 4+ exercises in one call (more output tokens, same one request
  per lesson) rather than more requests, so this isn't materially worse on
  free-tier quota than before.
- **Breaking the `Lesson.content` API field** — no other service or the
  frontend has any other consumer of `GET /api/lessons/*`'s shape besides
  this repo's own frontend, and this is a solo-dev app with no external API
  consumers, so there's no deprecation-period concern here.
- **Losing the 3 test accounts' progress rows** — explicitly accepted above,
  not a real risk given no real end users exist yet.
