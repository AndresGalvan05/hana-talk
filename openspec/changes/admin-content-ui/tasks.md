## 1. core-api: expose role

- [x] 1.1 Add `role: UserRole` to `UserProfileResponse`
      (`dto/UserProfileDtos.kt`); `UserProfileService.getProfile` already
      loads the full `User`, so this is a passthrough field, not a new
      query
- [x] 1.2 `UserProfileControllerTest`: existing `get profile returns user
      data` test asserts `$.role`

## 2. frontend: role awareness + admin gating

- [x] 2.1 `api/types.ts`: add `UserRole = 'USER' | 'ADMIN'`; add `role:
      UserRole` to `UserProfile`
- [x] 2.2 `api/client.ts`: `tokenStore` gains `getRole()`/`setRole(role)`
      (new `hanatalk.role` localStorage key, mirroring
      `TOKEN_KEY`/`USERNAME_KEY`), and cleared in `clear()`; add `put`
      and `delete` methods to the `api` object (`put` mirrors `patch`'s
      shape but with `method: 'PUT'`; `delete` has no body)
- [x] 2.3 `AuthContext.tsx`: new `role: UserRole | null` state,
      initialized from `tokenStore.getRole()`; `login`/`register` call
      `api.get<UserProfile>('/api/users/me')` right after the existing
      auth call succeeds, then `tokenStore.setRole(profile.role)` and
      update state; `logout` clears role via `tokenStore.clear()`
      (already clears everything)
- [x] 2.4 New `frontend/src/components/RequireAdmin.tsx`: structurally
      mirrors `RequireAuth.tsx`, checks `role === 'ADMIN'` from
      `useAuth()`, redirects to `/courses` (via `<Navigate>`) if not
      admin
- [x] 2.5 `Layout.tsx`: "Admin" nav link shown only when `role ===
      'ADMIN'`

## 3. frontend: reusable inline delete confirmation

- [x] 3.1 New `frontend/src/components/ConfirmDeleteButton.tsx`: takes
      `onConfirm: () => void` and a `label` prop; local state toggles
      between a single "Delete" button and an inline "Confirm"/"Cancel"
      pair; used by both the course and lesson admin list pages

## 4. frontend: admin course pages

- [x] 4.1 New `frontend/src/pages/AdminCoursesPage.tsx`: lists all
      courses (reuses `GET /api/courses`) with Edit/Delete
      (`ConfirmDeleteButton`) per row and a "New course" link
- [x] 4.2 New `frontend/src/pages/AdminCourseFormPage.tsx`: shared
      create/edit form (title text input, JLPT level `<select>`
      matching `ProfilePage`'s `LEVELS` constant, description textarea);
      on save, `POST /api/courses` (create) or `PUT /api/courses/{id}`
      (edit), then navigate back to `/admin/courses`
- [x] 4.3 Routes in `App.tsx` under `RequireAdmin`: `/admin/courses`,
      `/admin/courses/new`, `/admin/courses/:id/edit` (all three point
      at the two page components above, keyed by presence/value of `:id`)

## 5. frontend: admin lesson pages

- [x] 5.1 New `frontend/src/pages/AdminLessonsPage.tsx`: lists a course's
      lessons (`GET /api/courses/{courseId}/lessons`) with Edit/Delete
      per row and a "New lesson" link, plus a link back to the course's
      admin edit page
- [x] 5.2 New `frontend/src/pages/AdminLessonFormPage.tsx`: title text
      input, position number input, content `<textarea>` pre-filled with
      `JSON.stringify(lesson.content, null, 2)` on edit or a minimal
      valid `LessonContent` skeleton on create; on save, `JSON.parse` the
      textarea inside a `try/catch` — a parse failure sets a local error
      and returns without calling the API; on parse success, `POST
      /api/courses/{courseId}/lessons` (create) or `PUT
      /api/courses/{courseId}/lessons/{lessonId}` (edit), then navigate
      back to the lesson list
- [x] 5.3 Routes in `App.tsx` under `RequireAdmin`:
      `/admin/courses/:courseId/lessons`,
      `/admin/courses/:courseId/lessons/new`,
      `/admin/courses/:courseId/lessons/:lessonId/edit`

## 6. Tests & lint

- [x] 6.1 `sh gradlew ktlintCheck test --no-daemon` green
- [x] 6.2 `oxlint` and `tsc -b && vite build` both green

## 7. Local verification

- [x] 7.1 `docker compose up -d --build`, `npm run dev`; log in as a
      non-admin — confirm no "Admin" nav link and that navigating
      directly to `/admin/courses` redirects away. Verified: registered
      a fresh non-admin user, no "Admin" link shown, direct navigation to
      `/admin/courses` redirected to `/courses`.
- [x] 7.2 Grant a test user `ADMIN` directly in Postgres (matching the
      existing manual-grant pattern), log in again — confirm the "Admin"
      nav link appears. Verified via direct SQL `UPDATE users SET role =
      'ADMIN'`, confirmed `GET /api/users/me` returns the new role, and
      after a fresh login the "Admin" nav link renders.
- [x] 7.3 As admin: create a course, edit it, confirm changes show on
      the public `/courses` page; create a lesson with valid JSON
      content, confirm it renders correctly on the public lesson page;
      edit the lesson's content and confirm the change is reflected;
      submit deliberately malformed JSON and confirm the form blocks
      submission with an inline error and no network call (check via
      browser dev tools / network tab). Verified all of the above: a
      course created and edited via the UI showed correctly on the
      public `/courses` page; a lesson created with valid grammar
      point/dialogue/culture-note JSON rendered pixel-correct on the
      public lesson page; editing the lesson's content (clearing
      grammar/dialogue, changing the culture note) was reflected
      correctly; submitting malformed JSON showed "Content is not valid
      JSON." inline and the network tab confirmed zero `/api/` requests
      fired.
- [x] 7.4 Delete a lesson and a course via the inline confirm flow;
      confirm the delete does not fire on the first click, does fire
      after confirming, and the deleted item disappears from its list
      and from the public course/lesson pages. Verified: the first click
      swapped Delete to Confirm/Cancel with no deletion; the lesson
      remained present at that point; clicking Confirm deleted it and it
      disappeared from the lesson list. Same flow confirmed for
      deleting the test course, which then also disappeared from the
      public `/courses` page.

## 8. Production rollout

- [ ] 8.1 Deploy — merge to `main`, CI builds core-api and frontend
      images, `kubectl rollout restart` both (no migration)
- [ ] 8.2 Grant one real account `ADMIN` in production Postgres (if not
      already done for a prior manual-curl workflow) and spot-check the
      live site: admin nav appears, course/lesson create-edit-delete all
      work against production data

## 9. Docs

- [ ] 9.1 Update `docs/DEVLOG.md` (session entry) and `docs/ROADMAP.md`
      decision log
- [ ] 9.2 Update root `README.md` if it documents how content is
      currently authored (mentioning curl/Postman) to reflect the new
      admin UI
