# vocabulary-review Specification

## Purpose

Schedules per-user vocabulary review using a Leitner-style spaced-
repetition rule, and exposes the due queue and review submission so a
learner can actually retain vocabulary from lessons they've completed
instead of only seeing it once.

## Requirements

### Requirement: The due-review queue is scoped to vocabulary from completed lessons
The system SHALL return, for an authenticated user, the vocabulary items
belonging to lessons that user has completed, restricted to items that
are due: either never reviewed by that user, or whose scheduled next
review time has passed.

#### Scenario: Item never reviewed
- **WHEN** a user requests their review queue and a vocabulary item from
  one of their completed lessons has no review record for that user
- **THEN** that item is included in the queue

#### Scenario: Item due for review
- **WHEN** a user requests their review queue and a vocabulary item has a
  review record whose next review time has already passed
- **THEN** that item is included in the queue

#### Scenario: Item not yet due
- **WHEN** a user requests their review queue and a vocabulary item has a
  review record whose next review time is still in the future
- **THEN** that item is not included in the queue

#### Scenario: Vocabulary from an incomplete lesson is excluded
- **WHEN** a user requests their review queue and a vocabulary item
  belongs to a lesson the user has not completed
- **THEN** that item is not included in the queue, regardless of its
  review state

### Requirement: A review result reschedules the item using Leitner-style spacing
The system SHALL accept a correct/incorrect result for a vocabulary item
from an authenticated user, and SHALL reschedule that item's next review
time: on a correct result, the interval doubles from its previous value
(capped at 90 days) and the correct streak increments; on an incorrect
result, the interval resets to 1 day and the correct streak resets to 0.

#### Scenario: First-ever correct review
- **WHEN** a user submits a correct result for a vocabulary item with no
  prior review record
- **THEN** a review record is created with a 1-day interval and the next
  review scheduled 1 day out

#### Scenario: Correct review extends an existing interval
- **WHEN** a user submits a correct result for a vocabulary item whose
  current interval is N days
- **THEN** the interval becomes 2N days (capped at 90) and the next
  review is scheduled that many days out

#### Scenario: Incorrect review resets the interval
- **WHEN** a user submits an incorrect result for a vocabulary item,
  regardless of its current interval or streak
- **THEN** the interval resets to 1 day, the correct streak resets to 0,
  and the next review is scheduled 1 day out

#### Scenario: Interval is capped
- **WHEN** a correct result would double an interval past 90 days
- **THEN** the interval is set to 90 days, not the doubled value

### Requirement: Review endpoints require authentication
The system SHALL require authentication for both fetching the review
queue and submitting a review result, scoping all data to the
authenticated caller.

#### Scenario: Unauthenticated queue request
- **WHEN** a request for the review queue has no valid authentication
- **THEN** the system responds 401

#### Scenario: Unauthenticated review submission
- **WHEN** a request to submit a review result has no valid
  authentication
- **THEN** the system responds 401
