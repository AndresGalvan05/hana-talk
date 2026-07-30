## 1. Migration & entity

- [ ] 1.1 New `core-api/src/main/resources/db/migration/V13__create_vocabulary_progress.sql`:
      `user_vocabulary_progress(user_id, vocabulary_item_id,
      next_review_at TIMESTAMPTZ NOT NULL, interval_days INT NOT NULL,
      correct_streak INT NOT NULL, PRIMARY KEY (user_id,
      vocabulary_item_id))`, FK to `users(id)` and `vocabulary_items(id)`,
      both `ON DELETE CASCADE`
- [ ] 1.2 New `domain/vocabulary/UserVocabularyProgress.kt` +
      `UserVocabularyProgressId.kt` (`@EmbeddedId`, mirroring
      `UserLessonProgressId`) + `UserVocabularyProgressRepository.kt`
      (`findByIdUserId`, `findByIdOrNull` via Spring Data)
- [ ] 1.3 Add `findByLessonIdIn(lessonIds: List<UUID>):
      List<VocabularyItem>` to `VocabularyItemRepository`

## 2. Scheduling logic & service

- [ ] 2.1 New `service/VocabularyReviewService.kt`: `MAX_INTERVAL_DAYS =
      90` constant; `getDueQueue(userId): List<VocabularyItemResponse>`
      (completed lesson IDs → their vocabulary items → filter to items
      with no progress row or `nextReviewAt <= now`); `submitReview(userId,
      vocabularyItemId, correct: Boolean): VocabularyReviewResponse`
      (load existing progress or first-review defaults, apply Leitner
      doubling/reset, save, return the new schedule) — 404s if the
      vocabulary item doesn't exist
- [ ] 2.2 New DTOs in `api/dto/VocabularyReviewDtos.kt`:
      `ReviewResultRequest(correct: Boolean)`,
      `VocabularyReviewResponse(nextReviewAt: Instant, intervalDays: Int,
      correctStreak: Int)`

## 3. Controller

- [ ] 3.1 New `api/VocabularyReviewController.kt`:
      `GET /api/vocabulary/review` and
      `POST /api/vocabulary-items/{id}/review`, both requiring
      `@AuthenticationPrincipal`, resolving the user via
      `UserRepository.findByEmail` (same pattern as
      `ConversationController`/`UserProfileController`)

## 4. Tests

- [ ] 4.1 `VocabularyReviewServiceTest`: due-queue excludes items from
      incomplete lessons, includes never-reviewed items, includes
      past-due items, excludes not-yet-due items; first correct review
      sets a 1-day interval; correct review doubles an existing interval;
      doubling is capped at 90; incorrect review resets interval to 1 and
      streak to 0 regardless of prior state
- [ ] 4.2 `VocabularyReviewControllerTest` (`@WebMvcTest`): 401 on both
      endpoints without auth, 200 with a real queue/review result, 404
      reviewing a nonexistent vocabulary item
- [ ] 4.3 `ktlintCheck test` green

## 5. Frontend

- [ ] 5.1 Add `VocabularyReviewItem`/`VocabularyReviewResult` types to
      `frontend/src/api/types.ts`
- [ ] 5.2 New `frontend/src/pages/FlashcardsPage.tsx`: fetches the queue
      on mount, shows the current card's Japanese term, a reveal control,
      then correct/incorrect buttons once revealed; posts the result to
      `/api/vocabulary-items/{id}/review` and advances to the next queued
      item locally (no refetch needed — the queue was already fetched);
      shows a distinct "nothing due" state when the queue is empty or
      exhausted
- [ ] 5.3 New `/flashcards` route in `App.tsx`, nav link in `Layout.tsx`
- [ ] 5.4 New CSS in `index.css` for the flashcard (front/back reveal
      states, correct/incorrect buttons)
- [ ] 5.5 `oxlint` and `tsc -b && vite build` both green

## 6. Local verification

- [ ] 6.1 `docker compose up -d --build`; complete at least one lesson to
      get real vocabulary into the queue; open `/flashcards`, confirm the
      queue shows only that lesson's vocabulary, reveal a card, mark it
      correct, confirm it doesn't reappear until its new schedule
- [ ] 6.2 Mark a card incorrect, confirm it's still due immediately
      after (1-day interval means it won't show again today, but confirm
      the review was recorded with a reset interval/streak via a direct
      DB check, matching the lesson learned during `ai-conversation-practice`
      about not trusting UI state alone)
- [ ] 6.3 Confirm a lesson with zero completed lessons shows the "nothing
      due" empty state, not an error
- [ ] 6.4 `ktlintCheck test` (core-api) green

## 7. Production rollout

- [ ] 7.1 Deploy — merge to `main`, CI builds core-api and frontend
      images, `kubectl rollout restart` both (Flyway migrates
      automatically on core-api startup, same as every prior migration)
- [ ] 7.2 Spot-check the live site: open `/flashcards` on an account with
      at least one completed lesson, review a card, confirm it's recorded

## 8. Docs

- [ ] 8.1 Update `docs/DEVLOG.md` (session entry) and `docs/ROADMAP.md`
      decision log
- [ ] 8.2 Update root `README.md`'s "Still ahead" list (move vocabulary
      flashcards to "Shipped so far") and the Core API surface table
