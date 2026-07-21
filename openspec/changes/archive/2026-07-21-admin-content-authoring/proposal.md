## Why

`CourseController`/`LessonController` have had full POST/PUT/DELETE since
M1, but `SecurityConfig` only ever checks `.authenticated()` on them — any
registered user can currently create, edit, or delete any course or lesson.
This is a real, live security gap, not a hypothetical: content authoring
has no authorization boundary at all today. M5's "admin role for content
CRUD" closes it.

## What Changes

- `User` gains a `role` column (`UserRole.USER` | `UserRole.ADMIN`,
  defaulting to `USER`). Self-registration (`AuthController`/`AuthService`)
  **always** creates `USER` — there is no API path that can create or
  promote to `ADMIN`. Promoting a user is a direct SQL operation an
  operator runs against the database
  (`UPDATE users SET role = 'ADMIN' WHERE email = '...'`), the same way
  `core-api-secret` and the Cloudflare Origin CA secret were created
  directly on the cluster rather than through a checked-in bootstrap path —
  privilege escalation must never be self-service via a public endpoint.
- `UserDetailsServiceImpl` looks up the real `role` from the database
  (`.roles(user.role.name)`) instead of hardcoding `"USER"` for every
  principal. `JwtAuthFilter` already re-derives `UserDetails` from the
  database on every request (via `userDetailsService.loadUserByUsername`),
  so authorities are always current — no JWT `role` claim is added (see
  design.md's Decisions for why that was considered and rejected).
- `SecurityConfig` gains `hasRole("ADMIN")` matchers for the
  content-mutating endpoints that already exist: `POST/PUT/DELETE
  /api/courses/**` and `POST/PUT/DELETE /api/courses/*/lessons/**`,
  inserted before the existing `anyRequest().authenticated()` catch-all.
  All GET endpoints (courses, lessons, exercises) are unchanged — this
  only locks down mutation, not the public read surface M1 already
  established.
- **No new exercise CRUD endpoints.** Exercises are LLM-generated (M3), not
  manually authored — there is no existing create/update/delete exercise
  endpoint to lock down, and adding manual exercise authoring is a
  separate, unrequested feature, explicitly out of scope here.
- Existing `CourseControllerTest`/`LessonControllerTest` mutation tests
  (currently passing with a bare `@WithMockUser`, i.e. `ROLE_USER`, because
  no role check exists yet) are updated to use
  `@WithMockUser(roles = ["ADMIN"])`, and new tests assert a non-admin
  authenticated user gets `403` on the same endpoints.

## Capabilities

### New Capabilities
- `content-authoring-rbac`: the admin/user role model and the
  authorization boundary around course/lesson content mutation.

### Modified Capabilities
(none as delta specs — course/lesson CRUD predates OpenSpec adoption at M1,
so there is no existing base spec describing it to file a delta against;
the behavior change itself, tightening an unauthenticated-role gap to an
admin-only one, is captured as new requirements in `content-authoring-rbac`
rather than a modification)

## Impact

- **core-api**: `User.kt` (+`role` column), new Flyway
  `V10__add_role_to_users.sql`, `UserDetailsServiceImpl.kt` (real role
  lookup), `SecurityConfig.kt` (admin matchers), existing
  `CourseControllerTest.kt`/`LessonControllerTest.kt` updated, new 403
  test cases.
- **No frontend changes** — there is no admin UI in scope; this is purely
  an API-level authorization boundary. An admin using these endpoints today
  does so directly (curl/Postman), matching the portfolio scope (an admin
  UI would be a separate future change if ever prioritized).
- **Non-goals / cut line**: no admin UI, no self-service admin
  promotion/invitation flow, no exercise-authoring endpoints, no
  fine-grained permission system beyond a flat USER/ADMIN role, no
  audit log of content changes.
- **Milestone**: M5, per `docs/ROADMAP.md`'s "Order inside milestone"
  (step 3 — Grafana Cloud dashboards, step 2, can now proceed separately
  since the user has since created a Grafana Cloud account).
