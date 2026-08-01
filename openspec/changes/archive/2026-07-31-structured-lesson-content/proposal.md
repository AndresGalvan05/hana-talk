## Why

`expand-n5-curriculum-genki` grew the N5 course to 10 lessons, but each is
still a few paragraphs of flat text — nowhere near the depth of an actual
textbook chapter (Genki I: 7-8 grammar points, a vocab list, a dialogue, a
culture note, per chapter). The real problem isn't lesson count, it's that
`Lesson.content` is one opaque string with no structural concept of
"vocabulary," "grammar point," or "dialogue" — the app has no way to render
depth even if the text were longer. This replaces the content model itself
and consolidates into fewer, genuinely chapter-depth lessons, and lays the
schema groundwork (a real `vocabulary_items` table, not buried in text) that
later slices — new exercise types, AI conversation practice, spaced-repetition
flashcards, audio — all build on.

## What Changes

- **BREAKING**: `Lesson.content` (flat `TEXT`) is replaced by
  `Lesson.contentJson`, a JSON-serialized structure (vocabulary, grammar
  points with examples, a dialogue, a culture note) — same
  string-column-holding-JSON pattern `Exercise.optionsJson` already uses in
  this codebase, parsed into DTOs at the API layer.
- New `vocabulary_items` table (id, lesson_id, japanese, reading, meaning,
  position) — a first-class, queryable entity rather than JSON, since a
  future slice needs per-user review tracking against it.
- New migration replaces the current 10 lessons with **5** lessons, each
  mapped 1:1 to Genki I Lessons 1-5's topic scope, each authored with a full
  vocab list, 6-8 grammar points (with example sentences), an original
  dialogue, and a culture note. Content is original writing referencing
  Genki's topic sequence/scope only — never transcribed or closely
  paraphrased from the copyrighted textbook (explicit "no scanning/uploading"
  notice on the source PDF).
- Removes `V9`'s seeded placeholder exercises (lessons 1 and 4) — no longer
  needed now that every lesson generates real exercises on demand via
  `ai-exercise-svc`.
- `ai-exercise-svc`'s generation pipeline changes from a hardcoded "exactly
  one MCQ + one fill-in-blank" to "one or two exercises per grammar point,"
  driven by the new structured `grammarPoints` list instead of flat text.
- Frontend `LessonPage.tsx` is redesigned to render the new structure (vocab
  table, grammar-point cards, dialogue box, culture note) instead of a
  single `<pre>` block.
- **Non-goals / cut line for this slice**: new exercise types, AI
  conversation practice, flashcard review, and audio pronunciation are all
  explicitly deferred to their own future changes (per the approved
  multi-slice plan) — this slice is content model + content depth only.

## Capabilities

### New Capabilities
- `structured-lesson-content`: defines the structured content shape
  (vocabulary, grammar points, dialogue, culture note) every lesson must
  have, and that vocabulary is independently queryable per lesson.

### Modified Capabilities
- `curriculum-content`: the "at least ten lessons" requirement from
  `expand-n5-curriculum-genki` is replaced with "at least five lessons, each
  meeting the structured-content depth bar" — this change intentionally
  *reduces* lesson count while increasing depth per lesson, and the
  "rewriting preserves lesson identity" requirement no longer applies since
  lesson rows are being wholesale replaced, not edited in place (the small
  amount of existing test-account progress data resets, acceptable at this
  stage — no real end users yet).

## Impact

- **core-api**: `Lesson.kt` (`content` → `contentJson`), new
  `VocabularyItem` entity/repository, new `LessonContent` DTO tree, new
  Flyway migration (`V12`), `LessonService`/`LessonController` updated to
  serve structured content + vocabulary.
- **ai-exercise-svc**: `app/generation.py`, `app/schemas.py` — prompt and
  validator changes for per-grammar-point generation.
- **frontend**: `LessonPage.tsx` rewritten, new components for vocab table /
  grammar-point card / dialogue / culture note, new CSS in `index.css`.
- **No changes** to event-worker, infra/k8s, or auth/progress mechanics
  beyond the lesson content shape itself.
