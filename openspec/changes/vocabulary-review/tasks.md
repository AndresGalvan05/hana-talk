## 1. Migration & entity

- [x] 1.1 New `core-api/src/main/resources/db/migration/V13__create_vocabulary_progress.sql`:
      `user_vocabulary_progress(user_id, vocabulary_item_id,
      next_review_at TIMESTAMPTZ NOT NULL, interval_days INT NOT NULL,
      correct_streak INT NOT NULL, PRIMARY KEY (user_id,
      vocabulary_item_id))`, FK to `users(id)` and `vocabulary_items(id)`,
      both `ON DELETE CASCADE`
- [x] 1.2 New `domain/vocabulary/UserVocabularyProgress.kt` +
      `UserVocabularyProgressId.kt` (`@EmbeddedId`, mirroring
      `UserLessonProgressId`) + `UserVocabularyProgressRepository.kt`
      (`findByIdUserId`, `findByIdOrNull` via Spring Data)
- [x] 1.3 Added `findByLessonIdIn(lessonIds: List<UUID>):
      List<VocabularyItem>` to `VocabularyItemRepository`

## 2. Scheduling logic & service

- [x] 2.1 New `service/VocabularyReviewService.kt`: `MAX_INTERVAL_DAYS =
      90` constant; `getDueQueue(userId): List<VocabularyItemResponse>`
      (completed lesson IDs → their vocabulary items → filter to items
      with no progress row or `nextReviewAt <= now`); `submitReview(userId,
      vocabularyItemId, correct: Boolean): VocabularyReviewResponse`
      (load existing progress or first-review defaults, apply Leitner
      doubling/reset, save, return the new schedule) — 404s if the
      vocabulary item doesn't exist
- [x] 2.2 New DTOs in `api/dto/VocabularyReviewDtos.kt`:
      `ReviewResultRequest(correct: Boolean)`,
      `VocabularyReviewResponse(nextReviewAt: Instant, intervalDays: Int,
      correctStreak: Int)`

## 3. Controller

- [x] 3.1 New `api/VocabularyReviewController.kt`:
      `GET /api/vocabulary/review` and
      `POST /api/vocabulary-items/{id}/review`, both requiring
      `@AuthenticationPrincipal`, resolving the user via
      `UserRepository.findByEmail` (same pattern as
      `ConversationController`/`UserProfileController`); confirmed via
      `SecurityConfig` that both paths correctly fall under the
      `anyRequest().authenticated()` catch-all (no vocabulary-specific
      matcher exists, so nothing needed changing there)

## 4. Tests

- [x] 4.1 `VocabularyReviewServiceTest` (9 tests): due-queue excludes
      items from incomplete lessons, includes never-reviewed items,
      includes past-due items, excludes not-yet-due items; first correct
      review sets a 1-day interval; correct review doubles an existing
      interval; doubling is capped at 90; incorrect review resets
      interval to 1 and streak to 0 regardless of prior state; 404 on a
      nonexistent item. Caught and fixed a mockito-kotlin `any()` pitfall
      along the way: an un-parameterized `any()` against a generic
      `save<S extends T>(S)` method matched nothing, so the stub was
      never applied and every review threw a Kotlin null-check NPE on
      the return value — fixed with an explicit `any<UserVocabularyProgress>()`
- [x] 4.2 `VocabularyReviewControllerTest` (`@WebMvcTest`, 5 tests): 401
      on both endpoints without auth, 200 with a real queue/review
      result, 404 reviewing a nonexistent vocabulary item
- [x] 4.3 `ktlintCheck test` green

## 5. Frontend

- [x] 5.1 Added `VocabularyReviewResult` type to `frontend/src/api/types.ts`
      (reused the existing `VocabularyItem` type for queue entries — same
      shape, no need for a duplicate)
- [x] 5.2 New `frontend/src/pages/FlashcardsPage.tsx`: fetches the queue
      on mount, shows the current card's Japanese term, a reveal control,
      then correct/incorrect buttons once revealed; posts the result to
      `/api/vocabulary-items/{id}/review` and advances to the next queued
      item locally (no refetch needed — the queue was already fetched);
      shows a distinct "nothing due" state when the queue is empty or
      exhausted
- [x] 5.3 New `/flashcards` route in `App.tsx`, "Flashcards" nav link in
      `Layout.tsx`
- [x] 5.4 New CSS in `index.css` for the flashcard (front/back reveal
      states, correct/incorrect buttons)
- [x] 5.5 `oxlint` and `tsc -b && vite build` both green

## 6. Local verification

- [x] 6.1 `docker compose up -d --build` (confirmed V13 migrated via
      `flyway_schema_history`); completed lesson 1 to get real
      vocabulary into the queue; opened `/flashcards`, revealed the
      first card (わたし → watashi / I, me), marked it correct — advanced
      to the next card (あなた), unrevealed
- [x] 6.2 Marked the second card incorrect — confirmed via direct DB
      query (not just UI state, per the lesson learned during
      `ai-conversation-practice`) that わたし persisted with
      `interval_days=1, correct_streak=1` and あなた with
      `interval_days=1, correct_streak=0`
- [x] 6.3 Confirmed a fresh account with zero completed lessons shows the
      "Nothing due right now — check back later." empty state, not an
      error, before completing any lesson
- [x] 6.4 `ktlintCheck test` (core-api) green

## 7. Production rollout

- [x] 7.1 Deployed — merged to `main`, CI built core-api and frontend
      images, `kubectl rollout restart` both. `V13` migrated
      automatically on core-api startup (confirmed in pod logs:
      "Migrating schema... to version 13"). The image pull for the new
      core-api pod was unusually slow (~9 minutes, `ContainerCreating` →
      `Running` → ready) — no error events, node had no memory/disk/PID
      pressure, just a slow GHCR pull on the free-tier VM; waited it out
      rather than intervening
- [x] 7.2 Spot-checked the live site: registered/used an account,
      completed a lesson to get real vocabulary due, opened
      `/flashcards`, revealed and marked a card correct — confirmed via
      a direct query against the production database
      (`interval_days=1, correct_streak=1`), not just the UI, since a
      screenshot timed out at exactly that moment

## 8. Docs

- [x] 8.1 Updated `docs/DEVLOG.md` (session entry) and `docs/ROADMAP.md`
      decision log
- [x] 8.2 Updated root `README.md`'s "Still ahead" list (moved vocabulary
      flashcards to "Shipped so far") and the Core API surface table (new
      `GET /api/vocabulary/review`/`POST /api/vocabulary-items/{id}/review` row)
