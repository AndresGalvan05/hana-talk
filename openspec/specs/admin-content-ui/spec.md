# admin-content-ui Specification

## Purpose

Gives the app's admin a real, usable interface for the course/lesson
authoring API that `content-authoring-rbac` already gates correctly,
replacing curl/Postman as the only way to manage content.

## Requirements

### Requirement: The frontend shows admin-only navigation only to admins
The frontend SHALL determine the signed-in user's role after
authentication and SHALL show admin-only navigation and routes only when
that role is `ADMIN`, redirecting non-admin authenticated users away from
admin routes.

#### Scenario: An admin sees the admin nav link
- **WHEN** a user with the `ADMIN` role is signed in
- **THEN** an "Admin" navigation link is visible and admin routes are
  reachable

#### Scenario: A non-admin does not see the admin nav link
- **WHEN** a user with the `USER` role is signed in
- **THEN** no admin navigation link is shown

#### Scenario: A non-admin who navigates directly to an admin route is redirected
- **WHEN** an authenticated non-admin user navigates directly to an
  admin-only URL
- **THEN** they are redirected away from it without seeing admin content

### Requirement: Admins can create, edit, and delete courses through the UI
The frontend SHALL provide an admin interface to list, create, edit, and
delete courses, using the existing course CRUD API.

#### Scenario: Admin creates a course
- **WHEN** an admin submits a valid new-course form
- **THEN** the course is created and appears in the course list

#### Scenario: Admin edits a course
- **WHEN** an admin submits changes to an existing course's fields
- **THEN** the course is updated and the change is reflected in the list

#### Scenario: Admin deletes a course
- **WHEN** an admin confirms deletion of a course
- **THEN** the course is removed and no longer appears in the list

#### Scenario: Deletion requires explicit confirmation
- **WHEN** an admin clicks delete on a course
- **THEN** the deletion does not happen until a second, explicit
  confirmation action

### Requirement: Admins can create, edit, and delete lessons through the UI
The frontend SHALL provide an admin interface, scoped to a course, to
list, create, edit, and delete that course's lessons, including their
structured content, using the existing lesson CRUD API.

#### Scenario: Admin creates a lesson with valid structured content
- **WHEN** an admin submits a new-lesson form with well-formed JSON
  content matching the expected shape
- **THEN** the lesson is created and appears in that course's lesson list

#### Scenario: Admin edits an existing lesson's content
- **WHEN** an admin submits changes to an existing lesson's title,
  position, or content
- **THEN** the lesson is updated accordingly

#### Scenario: Malformed JSON is rejected before submission
- **WHEN** an admin submits a lesson form whose content field is not
  valid JSON
- **THEN** the form shows an error and does not call the API

#### Scenario: Admin deletes a lesson
- **WHEN** an admin confirms deletion of a lesson
- **THEN** the lesson is removed and no longer appears in that course's
  lesson list
