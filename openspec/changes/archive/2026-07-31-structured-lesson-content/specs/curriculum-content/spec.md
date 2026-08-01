## MODIFIED Requirements

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

## REMOVED Requirements

### Requirement: Expanding existing lesson content preserves lesson identity and progress validity
**Reason**: This change replaces the lesson set outright (10 shallow
lessons → 5 structured ones) rather than rewriting lesson rows in place.
Preserving `user_lesson_progress` continuity across that kind of content
replacement was judged not worth the complexity, given only test/demo
accounts have any progress rows at this stage — no real end users exist
yet to lose meaningful data.
**Migration**: None — any existing `user_lesson_progress` rows referencing
deleted lesson IDs are removed by the migration (`ON DELETE CASCADE`);
affected accounts simply start the new course from zero.

### Requirement: Rewritten lesson content remains consistent with any exercises already seeded against it
**Reason**: This requirement existed only because `V9` had seeded
placeholder exercises directly against specific lesson IDs, and rewrites
had to keep those lessons' quoted phrases intact. This change removes
`V9`'s seeded exercises entirely (every lesson now generates real exercises
on demand via `ai-exercise-svc`), so there is nothing left for a rewrite to
stay consistent with.
**Migration**: None — the seeded exercises are deleted, not migrated;
their lessons regenerate exercises on next request like any other lesson.
