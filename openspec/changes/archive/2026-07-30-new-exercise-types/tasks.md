## 1. core-api

- [x] 1.1 Add `TRANSLATION` and `SENTENCE_ORDERING` to `ExerciseType.kt`
- [x] 1.2 `ExerciseService.grade()`: added `TRANSLATION` (same
      trim+lowercase as `FILL_IN_BLANK`) and `SENTENCE_ORDERING` (exact
      match after trim, no case-folding) branches
- [x] 1.3 Added `translationExercise()`/`sentenceOrderingExercise()`
      fixtures and 3 new tests to `ExerciseServiceTest` (correct
      translation ignoring case/whitespace, correct sentence-ordering
      completes the lesson, right-tokens-wrong-order is incorrect);
      `ExerciseControllerTest` needed no changes (only ever fixtures MCQ,
      no exhaustive `when`). `ktlintCheck test` green (54 tests)

## 2. ai-exercise-svc

- [x] 2.1 Add `TRANSLATION` and `SENTENCE_ORDERING` to `ExerciseType` in
      `app/schemas.py`
- [x] 2.2 Extended `GeneratedExercise._check_options_match_type`:
      `SENTENCE_ORDERING` requires non-empty `options` with
      `sorted(options) == sorted(correct_answer.split())`; `TRANSLATION`
      requires no options (same branch as `FILL_IN_BLANK`)
- [x] 2.3 Updated `_PROMPT_TEMPLATE` to ask for a mix of all four exercise
      types across the batch, with format instructions for translation
      (English prompt, Japanese `correct_answer`, no options) and
      sentence-ordering (shuffled word tokens as `options`, correctly
      ordered tokens space-joined as `correct_answer`)
- [x] 2.4 `tests/test_generation.py`/`test_routes.py` fixtures already used
      generic MCQ/FILL_IN_BLANK exercises and needed no changes; added 5
      new tests to `tests/test_schemas.py` covering both new validator
      branches (translation-with-options rejected, empty-options rejected,
      token-mismatch rejected, matching-tokens-any-order accepted).
      `ruff check`/`ruff format`/`pytest` green (16 tests)

## 3. Frontend

- [x] 3.1 Added `TRANSLATION` and `SENTENCE_ORDERING` to `ExerciseType` in
      `api/types.ts`
- [x] 3.2 New `SentenceOrderingInput` component in `ExercisePractice.tsx`:
      click a shuffled token to append it, click a picked token to remove
      it back to available (handles repeated tokens like です appearing
      twice by removing one matching occurrence per pick, not filtering by
      value); submit sends the picked tokens space-joined. New CSS for
      `.sentence-ordering`/`.token`/`.token-picked` in `index.css`
- [x] 3.3 Confirmed `TRANSLATION` needs no new rendering — falls into the
      existing no-`options` text-input branch already used by
      `FILL_IN_BLANK`, unchanged. `oxlint` and `tsc -b && vite build` both
      green

## 4. Local verification

- [x] 4.1 `docker compose up -d --build`; requested exercises for lesson 4
      (never touched): 12 generated in one real call, type mix
      MCQ×4/FILL_IN_BLANK×3/TRANSLATION×3/SENTENCE_ORDERING×2 — the prompt's
      "mix of all four types" guidance worked without a hard validator
      requirement
- [x] 4.2 Browser check via Chrome automation: clicked all 7 shuffled
      tokens of a sentence-ordering exercise into order, confirmed the
      picked/available split renders correctly and updates live; submitted
      → "✅ Correct!". Translation exercise confirmed rendering via the
      existing text-input path with no code changes needed
- [x] 4.3 First browser attempt accidentally submitted one token short (6
      of 7) — graded "❌ Not quite, try again", correctly rejecting the
      incomplete/wrong-order answer; also verified directly via curl:
      right tokens submitted in the wrong order graded incorrect,
      unshuffled/correct order graded correct
- [x] 4.4 `ktlintCheck test` (core-api, 54 tests) and `ruff`/`pytest`
      (ai-exercise-svc, 16 tests) all green

## 5. Production rollout

- [x] 5.1 Deploy — merge to `main`, CI built core-api, ai-exercise-svc, and
      frontend images, `kubectl rollout restart` all three (no migration
      needed — `exercises.type` is already `VARCHAR(20)`)
- [x] 5.2 Spot-check the live site: requesting exercises for lesson 1
      surfaced a chain of three bugs (stale Mongo cache from lesson-UUID
      reuse across `V11`/`V12`, stale Postgres rows masking the Mongo fix,
      and a real generation bug — one malformed `SENTENCE_ORDERING` exercise
      invalidated the whole batch, exhausting all 3 LLM providers and
      failing after ~90s). All three fixed and verified live: lesson 1 now
      returns a genuine mix of all four exercise types in ~32s. Full
      writeup in `docs/DEVLOG.md`'s 2026-07-30 entry

## 6. Docs

- [x] 6.1 Updated `docs/DEVLOG.md` (session entry) and `docs/ROADMAP.md`
      decision log
- [x] 6.2 Updated root `README.md`'s "What's next" section — split into
      "Shipped so far"/"Still ahead", moving both structured lesson content
      (previous slice, also stale before this) and new exercise types out
      of "planned." Also fixed the core-api service-status row, still
      saying "Flyway V1–V11 (10-lesson)" — bumped to V12/5-chapter and
      added the new exercise types
