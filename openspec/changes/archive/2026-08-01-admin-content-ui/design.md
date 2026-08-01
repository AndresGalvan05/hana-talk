## Context

Server-side gating is already correct and unrelated to this change:
`SecurityConfig.kt` restricts `POST/PUT/DELETE` on `/api/courses` and
`/api/courses/*/lessons/*` to `hasRole("ADMIN")`, and role is re-derived
from the database on every request (`UserDetailsServiceImpl`) — there's
no JWT `role` claim to go stale. This change only has to teach the
*frontend* what the backend already enforces.

`AuthContext` (`frontend/src/auth/AuthContext.tsx`) currently persists
only `token`/`username` in `localStorage` via `tokenStore`, populated
synchronously from `login`/`register`'s response (`AuthResponse { token,
username }`) with no extra round trip. `UserProfileResponse` (returned by
the already-existing `GET /api/users/me`) has no `role` field today.

`api/client.ts`'s `api` object only has `get`/`post`/`patch` — `patch` is
used for the one existing partial-update endpoint (JLPT level). Course/
lesson updates use `PUT` per the existing controllers, and deletes need
`DELETE`, so both need adding.

No existing frontend form handles a nested object (`LessonContent` has
`grammarPoints: GrammarPoint[]`, each with `examples: ExampleSentence[]`,
plus `dialogue.lines[]`) — `ProfilePage`'s JLPT `<select>` is the most
complex form that exists today.

## Goals / Non-Goals

**Goals:**
- Frontend can determine admin status without changing the JWT.
- Course and lesson CRUD (the two entities that already have a backend
  API) get a real, usable admin UI.
- Destructive actions (delete) get a confirmation step that doesn't rely
  on a blocking native dialog.

**Non-Goals:** see proposal.md — no vocabulary CRUD, no nested-list
content editor, no preview, no role management UI, no audit log, no
media upload, no bulk import/export.

## Decisions

- **`UserProfileResponse` gains `role: UserRole`.** `UserProfileService`
  already loads the full `User` entity to build the response — this is
  passthrough, not a new query. `AuthContext.login`/`register` make one
  follow-up call to `GET /api/users/me` after the existing auth call
  succeeds, and persist the returned role via `tokenStore.setRole(...)`
  (new field alongside `token`/`username`) so it's available synchronously
  on every later page load without a further request or a role-flash on
  refresh. **Accepted trade-off**: if an admin's role changes via direct
  DB update while they have an open session, the frontend won't notice
  until their next login — acceptable for an app where admin grants are
  already a rare, manual, out-of-band action.
- **`RequireAdmin` is a new route wrapper**, structurally identical to
  the existing `RequireAuth` (`frontend/src/components/RequireAuth.tsx`)
  but checking `role === 'ADMIN'` and redirecting non-admins to `/courses`
  instead of `/login`. Kept as a separate component rather than adding an
  `adminOnly` prop to `RequireAuth`, since the two have different
  redirect targets and failure semantics (unauthenticated vs.
  unauthorized).
- **Course/lesson forms are plain controlled-input forms**, matching
  `ProfilePage`'s existing pattern (local `useState` per field, no form
  library) — the field count (title, JLPT level, description for
  courses; title, position, content for lessons) doesn't justify pulling
  in a form library for the first time in this codebase.
- **Lesson content (`grammarPoints`/`dialogue`/`cultureNote`) is edited as
  a single JSON `<textarea>`**, pre-filled with
  `JSON.stringify(lesson.content, null, 2)` on edit (or a minimal valid
  skeleton on create). On submit, `JSON.parse` the textarea value inside a
  `try/catch`; a parse failure shows an inline error and blocks
  submission without a network call. This intentionally does **not**
  validate the parsed shape against `LessonContent` beyond what the
  backend's own request validation already does — a second, duplicated
  client-side shape-validator would be more code than the feature it's
  protecting. A `422`/`400` from the API on genuinely malformed content
  (right JSON, wrong shape) surfaces via the existing generic
  error-message display, same as every other form's failure path.
- **Delete confirmation is inline, not `window.confirm()`.** Each
  row's Delete button, on first click, swaps to "Confirm" / "Cancel"
  buttons in place (local `useState<string | null>` tracking which row
  id is pending confirmation); a second click on Confirm calls the
  delete endpoint. Chosen over a native `confirm()` dialog for two
  reasons: it's consistent with the rest of the app's styling (native
  dialogs can't be styled), and it keeps the flow scriptable/testable
  via browser automation, which a blocking native dialog is not.
- **`api/client.ts`**: add `put: <T>(path, body) => request<T>(path, {
  method: 'PUT', body: JSON.stringify(body) })` and `delete: <T>(path) =>
  request<T>(path, { method: 'DELETE' })`, matching the existing
  `post`/`patch` shape exactly.
- **Route shape**: `/admin/courses` (list + create), `/admin/courses/:id`
  (edit), `/admin/courses/:courseId/lessons` (list + create),
  `/admin/courses/:courseId/lessons/:lessonId` (edit) — mirrors the
  existing learner-facing `/courses/:courseId/lessons/:lessonId` nesting
  convention rather than inventing a new URL shape.

## Risks / Trade-offs

- **[Risk] JSON-textarea editing is unforgiving for hand-authoring new
  lesson content from scratch** (no field-level guidance, easy to
  mistype a key name and get a confusing backend validation error).
  Accepted per the proposal's cut line — this tool is for one admin
  (the app's owner) editing content they already understand the shape
  of, not a general-audience CMS. Pre-filling a valid skeleton on
  "create new lesson" mitigates the worst of it.
- **[Risk] Role is stale until next login if changed externally mid-
  session.** Accepted — see Decisions above.
- **[Risk] No confirmation before navigating away from an unsaved edit.**
  Not addressed here — consistent with every other form in this app
  today (e.g. `ProfilePage`'s level select has no unsaved-changes guard
  either); not a new gap this change introduces.

## Migration Plan

None — the `role` column already exists (`V10__add_role_to_users.sql`
from `admin-content-authoring`), and all CRUD endpoints already exist.
This is a backend DTO passthrough plus a frontend-only change. Deploys
through the existing core-api and frontend CI → GHCR → `kubectl rollout
restart` paths.
