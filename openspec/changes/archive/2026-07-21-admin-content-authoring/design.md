## Context

`User` (`domain/user/User.kt`) has no role concept today.
`UserDetailsServiceImpl.loadUserByUsername` hardcodes
`.roles("USER")` for every principal, so `JwtAuthFilter`'s
`UsernamePasswordAuthenticationToken(userDetails, null,
userDetails.authorities)` always carries exactly `ROLE_USER`.
`SecurityConfig.authorizeHttpRequests` has no role-based matcher anywhere;
`CourseController`/`LessonController`'s POST/PUT/DELETE endpoints
(existing since M1) fall through to the final `anyRequest().authenticated()`
— any logged-in user can mutate any course or lesson today. `JwtService`
embeds no custom claims (just `subject`/`issuedAt`/`expiration`).
Migrations run `V1`–`V9`; the next is `V10`.

## Goals / Non-Goals

**Goals:**
- Close the actual security gap: course/lesson mutation requires
  `ROLE_ADMIN`.
- No path, anywhere, for a user to grant themselves or anyone else the
  admin role through the API.
- Minimal surface: reuse the JWT/`UserDetailsService` machinery that
  already exists rather than introducing a new claims/token scheme.

**Non-Goals:**
- No admin UI.
- No invitation/promotion API.
- No exercise-authoring endpoints (exercises are LLM-generated, not
  manually authored).
- No fine-grained permissions (e.g. per-course editor roles) — a flat
  USER/ADMIN split is the entire model.

## Decisions

**Role lives in the database, re-checked every request — no JWT `role`
claim.** `JwtAuthFilter` already calls
`userDetailsService.loadUserByUsername(...)` on every authenticated
request, which re-queries `UserRepository.findByEmail` fresh each time.
Adding a `role` claim to the JWT was considered and rejected: it would be
redundant (authorities already come from a live DB lookup, not the token)
and would introduce a staleness risk — a demoted admin's already-issued
token would keep asserting the old role in its claim until expiry if
anything ever read authorization from the claim instead of the live
lookup. Keeping the single source of truth in the database, checked fresh
every request, is both simpler and safer.

**`UserRole` enum (`USER`, `ADMIN`), not a boolean `isAdmin` flag.** An
enum leaves room for a future role without a schema change (even though
none is planned — see Non-Goals), and matches the existing
`JlptLevel`/`Language`/`CompletionSource` enum-as-Postgres-string pattern
already used throughout the domain layer.

**No API can create an admin.** `AuthService.register` always constructs a
`User` with `role = UserRole.USER` — there is no request field, no admin
flag, nothing an attacker could set. The only way a user becomes an admin
is a direct `UPDATE users SET role = 'ADMIN' WHERE email = '...'` run by
whoever operates the database — the same trust boundary already used for
`core-api-secret` and the Cloudflare Origin CA TLS secret (created
directly on the cluster, never through a checked-in bootstrap script).
Rejected alternative: a one-time bootstrap admin seeded via Flyway.
Rejected because it would mean either hardcoding a password hash into a
migration file that ships in a public GitHub repo, or leaving a
known-default admin account that must be manually rotated post-deploy —
both worse than "the operator runs one SQL statement," which needs no
special-casing anywhere in the app.

**`SecurityConfig` matchers added, not `@PreAuthorize` on controllers.**
Keeps every authorization rule in the one file that already owns
`authorizeHttpRequests`, consistent with how the codebase has always
expressed authz (method-level security annotations are never used
elsewhere in this codebase). New matchers:
```kotlin
it.requestMatchers(HttpMethod.POST, "/api/courses", "/api/courses/*/lessons")
    .hasRole("ADMIN")
it.requestMatchers(HttpMethod.PUT, "/api/courses/*", "/api/courses/*/lessons/*")
    .hasRole("ADMIN")
it.requestMatchers(HttpMethod.DELETE, "/api/courses/*", "/api/courses/*/lessons/*")
    .hasRole("ADMIN")
```
placed after the existing GET-permitAll/GET-authenticated rules and before
`anyRequest().authenticated()` — GET traffic is untouched, only the three
mutating verbs on course/lesson paths gain a role check.

## Risks / Trade-offs

- [No admin exists until an operator manually promotes one] → intentional
  (Decisions); documented in the runbook, not automated, by design.
- [Existing `CourseControllerTest`/`LessonControllerTest` mutation tests
  currently pass with a bare `@WithMockUser` and will need
  `roles = ["ADMIN"]` added] → expected, low-risk mechanical test update;
  new 403 cases are added alongside, not a replacement of coverage.
- [A flat USER/ADMIN role is coarse — an admin can edit any course, there's
  no per-course ownership] → accepted; this portfolio app has one
  operator, not a marketplace of content authors — Non-Goal.

## Migration Plan

`V10__add_role_to_users.sql`: `ALTER TABLE users ADD COLUMN role VARCHAR(10)
NOT NULL DEFAULT 'USER'`. Backward compatible — every existing row gets
`USER` automatically, no application code needs to run before or after in
a particular order. Rollback: revert core-api's image tag; the column stays
(harmless, unused by an older image) until the next deploy cleans it up if
ever needed — no destructive rollback step required.
