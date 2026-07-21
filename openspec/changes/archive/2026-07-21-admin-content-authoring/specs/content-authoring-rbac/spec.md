## ADDED Requirements

### Requirement: Users have a role of USER or ADMIN, defaulting to USER
The system SHALL assign every user a role of `USER` or `ADMIN`, and
self-registration SHALL always assign `USER` regardless of any request
content.

#### Scenario: New user registers
- **WHEN** a new user completes registration
- **THEN** their stored role is `USER`

#### Scenario: Registration cannot request an elevated role
- **WHEN** a registration request includes any field suggesting an
  elevated role
- **THEN** the created user's role is still `USER` — no request field
  changes it

### Requirement: Course and lesson mutation requires the ADMIN role
The system SHALL require the `ADMIN` role to create, update, or delete a
course or a lesson, and SHALL leave all read (GET) access to courses and
lessons unchanged for any authenticated or anonymous caller as it exists
today.

#### Scenario: Admin creates a course
- **WHEN** a user with the `ADMIN` role sends a course-create request
- **THEN** the course is created

#### Scenario: Non-admin user is denied course mutation
- **WHEN** an authenticated user without the `ADMIN` role sends a
  course-create, course-update, or course-delete request
- **THEN** the request is rejected with a 403 response and no change is
  made

#### Scenario: Non-admin user is denied lesson mutation
- **WHEN** an authenticated user without the `ADMIN` role sends a
  lesson-create, lesson-update, or lesson-delete request
- **THEN** the request is rejected with a 403 response and no change is
  made

#### Scenario: Unauthenticated request is still rejected as unauthenticated
- **WHEN** a request with no valid authentication attempts to create,
  update, or delete a course or lesson
- **THEN** the request is rejected as unauthenticated (401), not merely
  forbidden

#### Scenario: Reads remain unaffected
- **WHEN** any caller, authenticated or not, issues a GET request for
  courses, lessons, or exercises
- **THEN** the request succeeds exactly as it did before this change
