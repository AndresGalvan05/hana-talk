## 1. core-api domain model

- [x] 1.1 Add `VocabularyItem` entity + `VocabularyItemRepository`
      (`findByLessonId`), matching the existing `Exercise`/`ExerciseRepository`
      pattern — used `findByLessonIdOrderByPosition` instead of a plain
      `findByLessonId`, since vocabulary needs to render in a stable order
- [x] 1.2 Add `LessonContent` DTO tree (`GrammarPoint`, `ExampleSentence`,
      `Dialogue`, `DialogueLine`, `CultureNote`) parsed from
      `Lesson.contentJson` via `ObjectMapper`, mirroring how
      `Exercise.optionsJson` is parsed today
- [x] 1.3 Rename `Lesson.content` → `Lesson.contentJson`; update
      `LessonService`/`LessonController`/`LessonDtos.kt` so lesson responses
      include structured content (grammar points, dialogue, culture note).
      Vocabulary ended up as its own `VocabularyController`/`VocabularyService`
      (`GET /api/lessons/{lessonId}/vocabulary`, matching the existing
      top-level `/api/lessons/{lessonId}/exercises` routing convention,
      not nested under `/api/courses/**`) rather than embedded in the
      lesson response — matches the spec's "independently queryable"
      requirement literally, and keeps the lesson payload from growing
      unboundedly with vocabulary size
- [x] 1.4 Updated `LessonControllerTest` (structured content in request/
      response fixtures) and `ExerciseServiceTest` (this rename cascades
      into `ExerciseService.listByLesson`, which reads `lesson.content` —
      folded that into this task rather than leaving the build broken
      between tasks; see the ai-exercise-svc section below for the other
      half of that change)

## 2. Migration

- [x] 2.1 Write `V12__structured_n5_lessons.sql`: `vocabulary_items` table
      (FK to `lessons`, cascade delete), `lessons.content_json` column
      (renamed from `content`), delete the 10 existing lesson rows, insert
      5 new chapter-depth lessons. `V9`'s seeded exercises needed no
      separate DELETE -- `exercises.lesson_id` already cascades from the
      lesson delete. Vocabulary rows use `gen_random_uuid()` rather than
      fixed UUIDs (nothing references individual vocabulary item IDs by
      value, unlike lessons)
- [x] 2.2 Authored lesson 1 (New Friends: self-intro, nationality/occupation,
      negation, questions, Noun の Noun, も, greeting formulas) — 7 grammar
      points, 14 vocab items, original dialogue, culture note
- [x] 2.3 Authored lesson 2 (Shopping: これ/それ/あれ/どれ family,
      demonstrative adjectives, location words, だれの, casual negative,
      prices, ~ね) — 7 grammar points, 15 vocab items
- [x] 2.4 Authored lesson 3 (Making a Date: ます-form, を/に/で, time
      references, ~ませんか, frequency adverbs, word order, topic は) — 8
      grammar points, 15 vocab items
- [x] 2.5 Authored lesson 4 (The First Date: あります/います revisited,
      location words, past tense of です and verbs, も, duration, と) — 8
      grammar points, 14 vocab items
- [x] 2.6 Authored lesson 5 (Trip to Okinawa: い/な-adjectives, adjective
      past tense and noun-modification, すき/きらい, ~ましょう, counting
      with つ) — 7 grammar points, 15 vocab items

## 3. ai-exercise-svc

- [x] 3.1 `GenerateRequest` drops `content: str`, gains
      `grammar_points: list[GrammarPointInput]` (title + explanation)
- [x] 3.2 Rewrote `_PROMPT_TEMPLATE` to iterate grammar points, asking for
      one exercise per point (two for points covering multiple distinct
      rules)
- [x] 3.3 `GenerationResult._check_type_coverage` → `_check_minimum_variety`:
      at least one MCQ, at least one FILL_IN_BLANK, at least
      `MIN_EXERCISES = 4` total
- [x] 3.4 Updated `app/routes.py`'s `/generate` handler and core-api's
      `AiExerciseSvcClient`/`GenerateExercisesRequest` DTO (new
      `GrammarPointInputDto`) to send `grammar_points` instead of `content`;
      `ExerciseService.listByLesson` now parses `Lesson.contentJson` and
      maps `grammarPoints` to the new DTO shape
- [x] 3.5 Updated `tests/test_generation.py`/`test_routes.py` for the new
      request shape and variety floor (fixtures bumped from 2 to 4
      exercises); `ruff check`/`ruff format`/`pytest` all green

## 4. Frontend

- [x] 4.1 New components: `VocabularyTable`, `GrammarPointCard`,
      `DialogueBox`, `CultureNoteAside` (`frontend/src/components/`)
- [x] 4.2 Rewrote `LessonPage.tsx` to fetch structured content + a separate
      vocabulary request, rendering via the new components instead of the
      single `<pre>` block
- [x] 4.3 New CSS rules in `frontend/src/index.css`; removed the now-unused
      `.lesson-text` rule the `<pre>` block used
- [x] 4.4 Updated `frontend/src/api/types.ts`: `Lesson.content` is now a
      structured `LessonContent` (grammar points, dialogue, culture note);
      added `VocabularyItem`. `oxlint` and `tsc -b && vite build` both green

## 5. Local verification

- [x] 5.1 `docker compose up -d --build` against a stack already at V11 —
      Flyway log confirmed "Migrating schema public to version 12 -
      structured n5 lessons", no errors
- [x] 5.2 `GET /api/courses/{id}/lessons` returned 5 structured lessons
      (7-8 grammar points each); `GET /api/lessons/{id}/vocabulary`
      returned 14 items for lesson 1
- [x] 5.3 Browser check via Chrome automation (`get_page_text`, screenshots
      flaked on scroll so switched to text extraction): vocabulary table,
      all 7 grammar-point cards with examples, the dialogue box with
      speaker attribution, and the culture note all rendered correctly for
      lesson 1
- [x] 5.4 Requested exercises for lesson 1 (never touched before): 9
      exercises generated (well above the 4-exercise floor), covering
      nearly every grammar point, both MCQ and FILL_IN_BLANK present,
      ~63s for the real LLM call
- [x] 5.5 Submitted a correct MCQ answer; `{"correct":true}` and
      `GET .../progress` showed `{"completed":1,"total":5}` — total
      correctly reflects the new 5-lesson course
- [x] 5.6 `ktlintCheck test` (core-api, 51 tests) and `ruff check`/`pytest`
      (ai-exercise-svc, 12 tests) all green; also fixed a test-only bug
      found along the way -- `ExerciseServiceTest`'s hand-built
      `ObjectMapper()` lacked the Kotlin module Spring auto-registers in
      production, so it could not deserialize `LessonContent`

## 6. Production rollout

- [ ] 6.1 (User-executed) Deploy the normal way — merge to `main`, CI
      builds core-api and ai-exercise-svc images, `kubectl rollout restart`
      both deployments (Flyway runs `V12` automatically on core-api
      startup)
- [ ] 6.2 (User-executed) Spot-check the live site: 5 structured lessons
      render correctly, exercise generation works on a fresh lesson

## 7. Docs

- [x] 7.1 Updated `docs/DEVLOG.md` (session entry) and `docs/ROADMAP.md`
      decision log
- [x] 7.2 Updated `CLAUDE.md`'s seeded fixture data note — lesson IDs now
      `...0001`-`...0005`, and flagged that content is structured
      (`contentJson`) with vocabulary served separately
