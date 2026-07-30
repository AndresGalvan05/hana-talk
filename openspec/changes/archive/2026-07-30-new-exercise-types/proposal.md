## Why

Every exercise in the app, seeded or LLM-generated, has always been either
multiple-choice or fill-in-the-blank — two formats, no matter which of the
now much richer grammar points (per `structured-lesson-content`) it's
testing. Second slice of the approved post-roadmap plan: adding translation
and sentence-ordering exercises gives practice more variety and exercises
more grading formats that actually fit different kinds of grammar (word
order rules are hard to test meaningfully with MCQ/fill-in-blank alone).

## What Changes

- `ExerciseType` gains `TRANSLATION` and `SENTENCE_ORDERING` — extended
  independently in both `core-api` (Kotlin enum) and `ai-exercise-svc`
  (Python enum); the two services share no schema, so both need the change.
- Grading (`ExerciseService.grade()`, a single exhaustive `when` over
  `ExerciseType`): `TRANSLATION` grades the same way `FILL_IN_BLANK`
  already does (trim + lowercase exact match); `SENTENCE_ORDERING` grades
  the submitted answer as an exact match against a space-joined correct
  token sequence.
- `ai-exercise-svc`'s generation prompt is updated to ask for a mix of all
  four exercise types across a batch, and its schema validator gains rules
  for the two new types: `SENTENCE_ORDERING` requires non-empty `options`
  (the shuffled word tokens) whose token set exactly matches
  `correct_answer` split by spaces; `TRANSLATION` requires no options, same
  as `FILL_IN_BLANK`.
- Frontend (`ExercisePractice.tsx`): `TRANSLATION` needs no new UI — it
  already renders as a text input, the same branch `FILL_IN_BLANK` uses,
  since neither has `options`. `SENTENCE_ORDERING` gets a new click-to-order
  UI: click a shuffled word to append it to your answer, click it again
  (now in the "picked" list) to remove it — not full drag-and-drop, to stay
  finishable.
- **Non-goals / cut line**: no partial credit for sentence-ordering (exact
  sequence or nothing, matching every other exercise type's binary
  correct/incorrect); no new UI for translation; the existing generation
  floor (`MIN_EXERCISES = 4`, at least one MCQ and one fill-in-blank) is
  unchanged — the new types are prompted for but not hard-required per
  batch, to avoid making generation more failure-prone for smaller lessons
  or weaker fallback providers.

## Capabilities

### Modified Capabilities
- `exercise-grading`: grading rules extended to cover `TRANSLATION` and
  `SENTENCE_ORDERING`; the exercise-listing response now includes `options`
  for `SENTENCE_ORDERING` too, not just MCQ.
- `exercise-generation`: schema-validation rules extended to cover the two
  new types' shape requirements.

## Impact

- **core-api**: `ExerciseType.kt` (+2 enum values), `ExerciseService.kt`
  (`grade()` gains two branches), `ExerciseControllerTest`/
  `ExerciseServiceTest` updated for exhaustiveness.
- **ai-exercise-svc**: `app/schemas.py` (`ExerciseType` enum, validator
  rules), `app/generation.py` (prompt update).
- **frontend**: `ExercisePractice.tsx` gains a `SENTENCE_ORDERING` render
  branch; `api/types.ts`'s `ExerciseType` union extended.
- **No database migration** — `exercises.type` is already `VARCHAR(20)`,
  and `TRANSLATION`/`SENTENCE_ORDERING` both fit within that length.
