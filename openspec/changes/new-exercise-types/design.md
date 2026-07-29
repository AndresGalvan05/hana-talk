## Context

`ExerciseType` (`core-api/.../domain/exercise/ExerciseType.kt`, independently
mirrored in `ai-exercise-svc/app/schemas.py` — no shared schema between the
two services) currently has two values. Grading is centralized in exactly
one place, `ExerciseService.grade()`, a `when` with no `else` branch — the
Kotlin compiler forces exhaustive handling, so adding a case is a compile
error until handled, a real safety net. `Exercise`'s `optionsJson: String?`
already stores a JSON array for MCQ; `correctAnswer: String` is always a
single string field, for any type. `ExercisePractice.tsx`'s `ExerciseCard`
currently branches on `exercise.options ? <radio group> : <text input>`.

## Goals / Non-Goals

**Goals:**
- Add `TRANSLATION` (reuses the existing text-input + trim/lowercase
  grading path with zero new frontend or grading code).
- Add `SENTENCE_ORDERING` (a genuinely new shape: multiple tokens, order
  matters), reusing `optionsJson` for the shuffled tokens rather than
  adding a new column.
- Keep `ExerciseService.grade()` as the single place grading logic lives.

**Non-Goals:**
- Partial credit or fuzzy matching for sentence order.
- Full drag-and-drop reordering UI — click-to-append/click-to-remove only.
- Making all four types mandatory in every generated batch.

## Decisions

**`SENTENCE_ORDERING` reuses `optionsJson` (shuffled tokens) and
`correctAnswer` (tokens in correct order, space-joined) — no new entity
column.** `Exercise`'s existing two generic fields are already exactly the
right shape: a list of strings (options) and a single string (the answer to
compare against). Adding a third column just for one exercise type would
break the pattern every other type already follows.

**Grading**: `TRANSLATION` → identical to `FILL_IN_BLANK`
(`answer.trim().lowercase() == exercise.correctAnswer.trim().lowercase()`).
`SENTENCE_ORDERING` → `answer.trim() == exercise.correctAnswer.trim()`
(exact match, no case-folding — word order and exact tokens both matter,
unlike a fill-in-blank's forgiving casing). The frontend builds `answer` by
joining picked tokens with single spaces, matching how `correctAnswer` is
stored, so a fully-correct pick sequence produces an identical string with
no normalization needed on either side.

**ai-exercise-svc's `GeneratedExercise` validator gains a
`SENTENCE_ORDERING` rule**: `options` must be non-empty, and
`sorted(options) == sorted(correct_answer.split())` — the *set* of tokens in
`options` must exactly match the *set* of tokens in `correct_answer`, order
aside (options are shuffled, correct_answer is the unshuffled sequence).
This catches an LLM inventing extra words or dropping one, the same way the
MCQ rule already catches `correct_answer` not being among `options`.
`TRANSLATION` reuses the exact same "must not have options" branch
`FILL_IN_BLANK` already uses.

**Prompt change: request a type mix, but do not hard-require it.**
`_PROMPT_TEMPLATE` gets a line asking for "a mix of all four exercise types
across the batch, not just multiple-choice and fill-in-the-blank" — soft
guidance, not a validator rule. Rejected making `GenerationResult` require
at least one of every type: some lessons have as few as 7 grammar points,
and a hard 4-type floor risks generation failing more often on weaker
fallback models (Groq, OpenRouter) that may not reliably produce every
format on request. The existing floor (`MIN_EXERCISES = 4`, ≥1 MCQ, ≥1
FILL_IN_BLANK) already guarantees a working batch; the new types are a
quality improvement on top of that guarantee, not a new hard requirement
that could turn into a new failure mode.

**Frontend click-to-order UI, not drag-and-drop.** A `SentenceOrdering`-style
component tracks two local arrays: `available` (init from `exercise.options`)
and `picked` (init empty). Clicking a token in `available` moves it to the
end of `picked`; clicking a token in `picked` removes it and returns it to
`available` (order among remaining `available` tokens is preserved from the
original shuffle, not re-sorted). Submitting sends `picked.join(' ')` as the
answer. This is a small, fully keyboard-and-mouse-accessible interaction
with no drag library dependency — matches this project's existing zero
extra frontend dependencies beyond `react-router-dom`.

## Risks / Trade-offs

- **Two independently-maintained `ExerciseType` enums** (Kotlin, Python)
  already existed before this change and remain a known, accepted
  duplication — no shared schema/codegen between the services, consistent
  with how the project has handled this since M3.
- **`correctAnswer` as a space-joined string** for sentence-ordering is a
  slight overload of a field whose name suggests "the answer," not "the
  answer's canonical joined form" — acceptable since it is never exposed to
  the client (the exercise-listing response never includes `correctAnswer`,
  per the existing `exercise-grading` requirement), and grading logic is
  the only consumer.
