## ADDED Requirements

### Requirement: The seeded N5 course covers at least ten lessons following Genki I's topic sequence
The N5 course (`0b4f9a12-1111-4a5e-9d3c-000000000001`) SHALL contain at
least ten lessons, each covering a distinct grammar point or vocabulary
topic drawn from Genki I's first five lessons' worth of material, ordered
so each lesson's content does not depend on a grammar point introduced in
a later lesson.

#### Scenario: Fresh environment boots with the full expanded course
- **WHEN** a fresh environment (local compose, CI, or a rebuilt cluster)
  runs all Flyway migrations from empty
- **THEN** `GET /api/courses/{n5CourseId}/lessons` returns at least ten
  lessons in position order

#### Scenario: A later lesson does not require an earlier lesson to already exist
- **WHEN** lesson content at any position references a grammar point
- **THEN** that grammar point was introduced at an earlier position in the
  same course, never a later one

### Requirement: Expanding existing lesson content preserves lesson identity and progress validity
Rewriting an existing lesson's content SHALL NOT change that lesson's `id`,
`position`, or `course_id`, so that existing `user_lesson_progress` rows
referencing it remain valid.

#### Scenario: A previously-completed lesson stays marked complete after its content is rewritten
- **WHEN** a user had already completed a lesson before its content was
  expanded
- **THEN** `GET /api/courses/{id}/progress` still reports that lesson as
  completed after the rewrite, with no change to the user's completion
  count

### Requirement: Rewritten lesson content remains consistent with any exercises already seeded against it
If a lesson has exercises seeded directly (via Flyway, not
`ai-exercise-svc`) that quote specific vocabulary or grammar from that
lesson's content, rewriting the lesson's content SHALL preserve those exact
quoted terms.

#### Scenario: A seeded exercise's correct answer remains taught by the lesson
- **WHEN** a lesson has a Flyway-seeded exercise whose prompt or correct
  answer quotes a specific word or grammar pattern from that lesson
- **THEN** the rewritten lesson content still teaches that exact word or
  pattern, so the seeded exercise remains factually correct

### Requirement: New lessons require no separate exercise seeding
A newly added lesson SHALL NOT require Flyway-seeded exercises to be
usable — it relies on the existing on-demand generation path
(`ai-exercise-svc`, cached in MongoDB on first request) exactly as
lessons without seeded exercises already do.

#### Scenario: A new lesson's exercises are generated on first request
- **WHEN** a new lesson with zero `Exercise` rows is requested via
  `GET /api/lessons/{id}/exercises` for the first time
- **THEN** exercises are generated and persisted exactly as they already
  are for any other lesson with no seeded exercises, with no lesson-specific
  code path
