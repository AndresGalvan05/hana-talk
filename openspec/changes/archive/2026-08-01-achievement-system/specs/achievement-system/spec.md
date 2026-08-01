## Purpose

Gives users a visible, persistent reward for crossing streak and
lesson-completion milestones, derived from state `event-worker` already
maintains rather than tracked as a new, independent event stream.

## ADDED Requirements

### Requirement: Distinct lesson completions are counted idempotently, independent of daily activity
`event-worker` SHALL record each distinct `(user, lesson)` completion
exactly once, regardless of how many other lessons that user completes on
the same UTC day or whether the same `exercise.completed` event is
redelivered.

#### Scenario: A new lesson completion is counted
- **WHEN** `event-worker` consumes an `exercise.completed` event for a
  `(user, lesson)` pair it has not seen before
- **THEN** that lesson is recorded as completed for that user, and the
  user's total distinct-lesson-completion count increases by one

#### Scenario: A second distinct lesson on the same day is still counted
- **WHEN** a user completes a second, different lesson on a UTC day that
  already has a recorded activity day for that user
- **THEN** the second lesson is still recorded as a new distinct
  completion, even though the day itself was already recorded

#### Scenario: A redelivered event is not double-counted
- **WHEN** `event-worker` consumes the same `exercise.completed` event
  twice for the same `(user, lesson)` pair
- **THEN** the second delivery does not increase the completion count or
  otherwise change stored state

### Requirement: Achievements unlock automatically when a user crosses a defined threshold
`event-worker` SHALL maintain a fixed catalog of achievements, each
defined by a kind (`streak` or `completions`) and a threshold, and SHALL
persist an unlock for a given user and achievement the first time that
user's current value for the achievement's kind meets or exceeds its
threshold.

#### Scenario: A streak threshold is crossed
- **WHEN** a user's current streak reaches or exceeds a streak-kind
  achievement's threshold for the first time
- **THEN** that achievement is unlocked and persisted for that user with
  an unlock timestamp

#### Scenario: A completion-count threshold is crossed
- **WHEN** a user's total distinct lesson completions reaches or exceeds
  a completions-kind achievement's threshold for the first time
- **THEN** that achievement is unlocked and persisted for that user with
  an unlock timestamp

#### Scenario: An already-unlocked achievement is not re-unlocked
- **WHEN** an event is processed for a user who has already unlocked a
  given achievement
- **THEN** no duplicate unlock record is created and the original unlock
  timestamp is unchanged

#### Scenario: Achievements are evaluated per user, independently
- **WHEN** two different users have different streaks and completion
  counts
- **THEN** each user's unlocked achievements reflect only their own state

### Requirement: A user's full achievement catalog with unlock status is retrievable
The system SHALL expose each authenticated user's complete achievement
catalog — including achievements they have not yet unlocked — through
`GET /api/users/me/achievements`, with each entry indicating whether it
is unlocked and, if so, when.

#### Scenario: A user with some unlocked achievements
- **WHEN** an authenticated user who has unlocked some but not all
  achievements requests their achievement list
- **THEN** the response includes every achievement in the catalog, with
  unlocked entries showing an unlock timestamp and locked entries showing
  none

#### Scenario: A user with no unlocked achievements
- **WHEN** an authenticated user who has not unlocked any achievements
  requests their achievement list
- **THEN** the response includes the full catalog with every entry marked
  as locked
