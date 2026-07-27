## 1. Content authoring

- [x] 1.1 Rewrite lesson 1 (Greetings) with more vocabulary and deeper
      grammar notes, keeping `ありがとうございます` and `すみません` intact
      (quoted verbatim by two seeded exercises)
- [x] 1.2 Rewrite lesson 2 (Self-introduction) with more depth
- [x] 1.3 Rewrite lesson 3 (Numbers 1–10) with more depth
- [x] 1.4 Rewrite lesson 4 (The AはBです pattern) with more depth, keeping
      the topic particle は and copula です in the わたしは...です pattern
      intact (quoted verbatim by two seeded exercises)
- [x] 1.5 Rewrite lesson 5 (これ/それ/あれ) with more depth
- [x] 1.6 Write new lesson 6: あります/います (existence) + location words
- [x] 1.7 Write new lesson 7: telling time + basic time expressions
- [x] 1.8 Write new lesson 8: daily-routine verbs (ます-form) + を/に/で
      particles
- [x] 1.9 Write new lesson 9: past tense (~ました/~ませんでした)
- [x] 1.10 Write new lesson 10: い-adjectives/な-adjectives +
      すき/きらい (likes/dislikes)

## 2. Migration

- [x] 2.1 Write `V11__expand_n5_lessons.sql`: five `UPDATE lessons SET
      content = ... WHERE id = '<existing-uuid>'` statements for lessons
      1–5, then five `INSERT INTO lessons` statements for positions 6–10
      using UUIDs `0b4f9a12-2222-4a5e-9d3c-00000000000{6..10}` — fixed one
      slip while writing it (lesson 10 briefly used hex `a` instead of
      decimal `10`, caught before running the migration)

## 3. Local verification

- [x] 3.1 `docker compose up -d --build` — confirmed `V11` applies cleanly
      on top of `V1`–`V10` (Flyway log: "Migrating schema public to version
      11"). Also fixed an unrelated local-only bug found along the way: a
      typo in gitignored `infra/.env` (`claLLM_KEYS_ENV_PATH` instead of
      `LLM_KEYS_ENV_PATH`) was silently breaking the LLM keys wiring for
      local `ai-exercise-svc` verification
- [x] 3.2 `GET /api/courses/{id}/lessons` returns all 10 lessons in
      position order with the expected titles and full content
- [x] 3.3 Verified via API (not browser — equivalent request path): both
      seeded exercises each on lessons 1 and 4 still return correctly and
      grade `correct:true` against their original answers
      (ありがとうございます→"Thank you", すみません→"sumimasen",
      is→"は", です→"です")
- [x] 3.4 Verified via API: lesson 6's exercises generated live on first
      request (~25s, real Gemini call), content genuinely specific to the
      lesson (quizzed あります vs います and した), graded correctly, and
      completion propagated to course progress
- [x] 3.5 Confirmed via a fresh test user completing lessons 1, 4, and 6:
      `GET /api/courses/{id}/progress` correctly reported
      `{"completed":3,"total":10}` — total reflects the new 10-lesson
      course and completions accumulate correctly across old and new
      lessons alike

## 4. Production rollout

- [ ] 4.1 (User-executed) Deploy the migration to production the normal
      way — merge to `main`, CI builds the image, `kubectl rollout restart
      deployment/core-api` picks it up (Flyway runs automatically on
      startup, same as every prior migration)
- [ ] 4.2 (User-executed) Spot-check the live site: course now shows 10
      lessons, previously-completed lessons for any real registered users
      remain marked complete

## 5. Docs

- [x] 5.1 Update `docs/DEVLOG.md` (session entry) and `docs/ROADMAP.md`
      (decision log entry — note this is post-roadmap content work, not a
      gap in M1–M5)
- [x] 5.2 Updated `README.md`'s stale "Flyway V1–V7" reference (now
      V1–V11) and `CLAUDE.md`'s fixture lesson-ID range (now 10 lessons,
      `...0001`–`...0010`), both found while checking for staleness
