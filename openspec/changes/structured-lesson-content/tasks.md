## 1. core-api domain model

- [ ] 1.1 Add `VocabularyItem` entity + `VocabularyItemRepository`
      (`findByLessonId`), matching the existing `Exercise`/`ExerciseRepository`
      pattern
- [ ] 1.2 Add `LessonContent` DTO tree (`GrammarPoint`, `ExampleSentence`,
      `Dialogue`, `DialogueLine`, `CultureNote`) parsed from
      `Lesson.contentJson` via `ObjectMapper`, mirroring how
      `Exercise.optionsJson` is parsed today
- [ ] 1.3 Rename `Lesson.content` → `Lesson.contentJson`; update
      `LessonService`/`LessonController`/`LessonDtos.kt` so lesson responses
      include structured content (grammar points, dialogue, culture note)
      plus a separately-served vocabulary list
- [ ] 1.4 Update `LessonControllerTest`/any test fixtures referencing the
      old flat `content` field

## 2. Migration

- [ ] 2.1 Write `V12__structured_n5_lessons.sql`: `vocabulary_items` table
      (FK to `lessons`, cascade delete), `lessons.content_json` column
      (replacing `content`), delete `V9`'s seeded exercises, delete the 10
      existing lesson rows, insert 5 new chapter-depth lessons
- [ ] 2.2 Author lesson 1 content (New Friends: self-intro, X は Y です,
      questions, Noun の Noun) — vocab list, 6-8 grammar points with
      examples, original dialogue, culture note
- [ ] 2.3 Author lesson 2 content (Shopping: これ/それ/あれ/どれ family,
      demonstrative adjectives, location words, も)
- [ ] 2.4 Author lesson 3 content (Making a Date: ます-form verb
      conjugation, particles, time references, word order)
- [ ] 2.5 Author lesson 4 content (The First Date: あります/います, past
      tense of です and verbs, と)
- [ ] 2.6 Author lesson 5 content (Trip to Okinawa: adjectives
      present/past/noun-modifying, すき/きらい, counting)

## 3. ai-exercise-svc

- [ ] 3.1 `GenerateRequest` drops `content: str`, gains
      `grammar_points: list[GrammarPointInput]` (title + explanation)
- [ ] 3.2 Rewrite `_PROMPT_TEMPLATE` to iterate grammar points, asking for
      one exercise per point (two for points covering multiple distinct
      rules)
- [ ] 3.3 `GenerationResult._check_type_coverage` → `_check_minimum_variety`:
      at least one MCQ, at least one FILL_IN_BLANK, at least
      `MIN_EXERCISES = 4` total
- [ ] 3.4 Update `app/routes.py`'s `/generate` handler and core-api's
      `AiExerciseSvcClient`/request DTO to send `grammar_points` instead of
      `content`
- [ ] 3.5 Update `tests/test_generation.py`/`test_routes.py` for the new
      request shape and variety-floor validator

## 4. Frontend

- [ ] 4.1 New components: `VocabularyTable`, `GrammarPointCard`,
      `DialogueBox`, `CultureNoteAside` (`frontend/src/components/`)
- [ ] 4.2 Rewrite `LessonPage.tsx` to fetch and render structured content +
      vocabulary instead of the single `<pre>` block
- [ ] 4.3 New CSS rules in `frontend/src/index.css` for the new components
- [ ] 4.4 Update `frontend/src/api/types.ts` for the new lesson/vocabulary
      response shapes

## 5. Local verification

- [ ] 5.1 `docker compose up -d --build` against a stack with existing
      migration history — confirm `V12` applies cleanly
- [ ] 5.2 `GET /api/courses/{id}/lessons` returns 5 lessons with structured
      content; vocabulary fetch returns items per lesson
- [ ] 5.3 Browser check (Chrome automation): vocab table, grammar-point
      cards, dialogue, and culture note all render correctly for at least
      one lesson
- [ ] 5.4 Request exercises for a new lesson; confirm more than 2 exercises
      generate, covering more than one grammar point, still including at
      least one MCQ and one fill-in-blank
- [ ] 5.5 Complete a lesson via a correct exercise attempt; confirm
      progress/completion still works end to end
- [ ] 5.6 `ktlintCheck test` (core-api) and `pytest` (ai-exercise-svc) green

## 6. Production rollout

- [ ] 6.1 (User-executed) Deploy the normal way — merge to `main`, CI
      builds core-api and ai-exercise-svc images, `kubectl rollout restart`
      both deployments (Flyway runs `V12` automatically on core-api
      startup)
- [ ] 6.2 (User-executed) Spot-check the live site: 5 structured lessons
      render correctly, exercise generation works on a fresh lesson

## 7. Docs

- [ ] 7.1 Update `docs/DEVLOG.md` and `docs/ROADMAP.md` decision log
- [ ] 7.2 Update `CLAUDE.md`'s seeded fixture data note (lesson count/IDs
      changed again)
