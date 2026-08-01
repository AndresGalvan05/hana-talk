# curriculum-content

## Purpose

Defines the depth and structural requirements the seeded N5 course's
lesson content must meet.

## Requirements

### Requirement: The seeded N5 course covers at least five lessons following Genki I's topic sequence, each meeting a structural depth bar
The N5 course (`0b4f9a12-1111-4a5e-9d3c-000000000001`) SHALL contain at
least five lessons, each mapped to a distinct chapter's worth of Genki I
material, ordered so each lesson's content does not depend on a grammar
point introduced in a later lesson. Each lesson SHALL meet the structural
depth bar defined by the `structured-lesson-content` capability (multiple
grammar points, a vocabulary list, a dialogue, a culture note) — lesson
count decreased from the prior ten-lesson minimum specifically because
depth per lesson increased enough that fewer, denser lessons cover the same
material more thoroughly.

#### Scenario: Fresh environment boots with the full course
- **WHEN** a fresh environment (local compose, CI, or a rebuilt cluster)
  runs all Flyway migrations from empty
- **THEN** `GET /api/courses/{n5CourseId}/lessons` returns at least five
  lessons in position order, each with multiple grammar points, a
  vocabulary list, a dialogue, and a culture note

#### Scenario: A later lesson does not require an earlier lesson to already exist
- **WHEN** lesson content at any position references a grammar point
- **THEN** that grammar point was introduced at an earlier position in the
  same course, never a later one

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
