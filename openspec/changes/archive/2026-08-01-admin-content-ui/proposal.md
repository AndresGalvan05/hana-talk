## Why

`admin-content-authoring` (shipped earlier) added an `ADMIN` role and a
full course/lesson CRUD API — but no frontend ever consumed it. Today the
only way to create or edit a course or lesson is `curl`/Postman against
production, which isn't a real workflow and isn't something an interview
conversation can point to as "done." This closes that gap: an actual
admin UI for the API surface that already exists and is already gated
correctly server-side.

## What Changes

- The frontend learns whether the signed-in user is an admin (today it
  has zero visibility into role at all) and shows an "Admin" nav link
  only for admins, guarded by a new `RequireAdmin` route wrapper mirroring
  the existing `RequireAuth`.
- New admin pages: a course list (create / edit / delete) and, per
  course, a lesson list (create / edit / delete). Editing a lesson's
  structured content (grammar points, dialogue, culture note) is done via
  a JSON textarea validated client-side against the same shape the API
  expects, not a bespoke nested-list form builder.
- `api/client.ts` gets `put`/`delete` helpers (only `get`/`post`/`patch`
  exist today) since course/lesson updates and deletes need them.
- A first-of-its-kind, non-native delete confirmation (click Delete →
  button becomes Confirm/Cancel inline) rather than `window.confirm()`.

## Capabilities

### New Capabilities
- `admin-content-ui`: admin-status visibility on the frontend, the
  admin-only routes/nav, and the course/lesson management pages.

### Modified Capabilities
- `content-authoring-rbac`: `GET /api/users/me` gains a `role` field so
  the frontend can determine admin status. No change to who is allowed to
  do what — purely exposing existing state.

## Impact

- `core-api/`: `UserProfileResponse` gains `role: UserRole`; no new
  endpoints (course/lesson CRUD already exists and is already
  admin-gated in `SecurityConfig`).
- `frontend/`: `AuthContext` fetches and persists role after login/
  register; new `RequireAdmin` component; new `AdminCoursesPage`,
  `AdminCourseFormPage`, `AdminLessonsPage`, `AdminLessonFormPage`; new
  nav link; `api/client.ts` gains `put`/`delete`.
- No database migration (the `role` column already exists from
  `admin-content-authoring`).

## Non-goals / cut line

- **No vocabulary CRUD.** The vocabulary API is read-only today
  (`GET /api/lessons/{id}/vocabulary` only) — adding create/update/delete
  endpoints is real, separate backend work, not a frontend-only gap like
  courses/lessons. A future slice if it's ever needed.
- **No nested list-editor UI for grammar points / dialogue / culture
  note.** These are structured, typed fields, and a full add/remove/
  reorder editor for three levels of nesting (grammar points → each with
  a list of example sentences; dialogue → a list of lines) would be the
  single largest UI surface in the app for content only one person (the
  admin) ever touches. A JSON textarea with client-side `JSON.parse` +
  shape validation before submit is a deliberately smaller, safer trade:
  real editing capability without a bespoke form-builder.
- **No content preview matching the live student-facing lesson page.**
  The admin trusts what they typed; they can always open the real lesson
  page in another tab to check. Worth adding later if hand-authoring
  structured content by JSON turns out to be error-prone in practice.
- **No role-management UI.** Granting admin stays a manual `UPDATE users
  SET role = 'ADMIN'` against the cluster, exactly as decided when
  `admin-content-authoring` shipped — reaffirmed, not revisited.
- **No audit log or edit history** for content changes.
- **No image/media upload** — nothing in the current content model has
  unstructured media fields.
- **No bulk import/export.**

## Milestone

Post-roadmap, third slice after `chat-rate-limiting` and
`achievement-system` in the post-roadmap planning session's list — the
last of the three "close a known gap" slices before extended
observability (`ai-exercise-svc`/`event-worker` tracing/metrics), which
remains the one item still on the table after this.
