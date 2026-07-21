## 1. Role model

- [x] 1.1 Add `UserRole` enum (`USER`, `ADMIN`) under
      `domain/user/` or `domain/`, matching the existing
      `JlptLevel`/`Language`/`CompletionSource` enum style
- [x] 1.2 Add `V10__add_role_to_users.sql`: `ALTER TABLE users ADD COLUMN
      role VARCHAR(10) NOT NULL DEFAULT 'USER'`
- [x] 1.3 Add `role: UserRole` field to `User.kt` (`@Enumerated(EnumType.STRING)`,
      matching the `Course.jlptLevel` pattern), defaulting to `UserRole.USER`
- [x] 1.4 Confirm `AuthService`'s registration path never sets `role` from
      request input — it should rely entirely on the entity's default

## 2. Authentication wiring

- [x] 2.1 `UserDetailsServiceImpl.loadUserByUsername`: replace the
      hardcoded `.roles("USER")` with `.roles(user.role.name)`
- [x] 2.2 Confirm `JwtAuthFilter` needs no changes (it already re-derives
      `UserDetails`, including authorities, via `loadUserByUsername` on
      every request)

## 3. Authorization

- [x] 3.1 `SecurityConfig`: add `hasRole("ADMIN")` matchers for
      `POST`/`PUT`/`DELETE` on `/api/courses` and
      `/api/courses/*/lessons` paths, placed before the existing
      `anyRequest().authenticated()` catch-all
- [x] 3.2 Confirm GET matchers (courses/lessons/exercises, all currently
      `permitAll` or plain `authenticated`) are untouched

## 4. Tests

- [x] 4.1 Update `CourseControllerTest.kt`'s existing create/update/delete
      tests to use `@WithMockUser(roles = ["ADMIN"])`
- [x] 4.2 Update `LessonControllerTest.kt`'s existing create/update/delete
      tests the same way
- [x] 4.3 Add new tests: a non-admin authenticated user (`@WithMockUser`,
      default `ROLE_USER`) gets 403 on course create/update/delete
- [x] 4.4 Add new tests: a non-admin authenticated user gets 403 on lesson
      create/update/delete
- [x] 4.5 Confirm unauthenticated requests to these endpoints still return
      401, not 403 (pre-existing tests, should need no change)
- [x] 4.6 `sh gradlew ktlintCheck test bootJar` green

## 5. Verification

- [x] 5.1 `docker compose up -d --build`: confirm Flyway migrates cleanly
      from the existing seeded data through `V10`
- [x] 5.2 Register a fresh user, confirm their role is `USER` via a direct
      DB query; confirm they get 403 attempting to create a course
- [x] 5.3 Manually promote that user to `ADMIN` via SQL, confirm they can
      now create/update/delete a course and a lesson
- [x] 5.4 Confirm GET `/api/courses`, `/api/courses/{id}/lessons`, and
      `/api/lessons/{id}/exercises` all still work unauthenticated/
      authenticated exactly as before, for both the USER and ADMIN account
- [x] 5.5 Update `docs/ROADMAP.md` (this M5 step done), `docs/DEVLOG.md`
      (session entry), and add a short note to `infra/k8s/README.md` or
      the root README on how to promote a user to admin (the SQL
      statement, not an API)
