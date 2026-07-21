# exercise-practice-ui Specification

## Purpose
TBD - created by archiving change exercise-practice-ui. Update Purpose after archive.
## Requirements
### Requirement: The lesson page displays practice exercises for the current lesson
The frontend SHALL fetch and render the exercises for the current lesson on
the lesson page, showing each exercise's prompt and, for multiple-choice
exercises, its options — without ever displaying a correct answer before it
is submitted.

#### Scenario: Lesson has exercises
- **WHEN** a user opens a lesson that has one or more exercises
- **THEN** each exercise is rendered as its own card with a prompt and an
  answer input (radio options for MCQ, a text field for fill-in-blank)

#### Scenario: Lesson has no exercises yet
- **WHEN** a user opens a lesson for the first time and exercise generation
  is still in progress
- **THEN** the page shows a loading indicator, and upgrades its message
  after a short delay to indicate first-time generation can take up to a
  minute, rather than appearing stuck with no explanation

### Requirement: Submitting an exercise answer shows correct/incorrect feedback and allows retry
The frontend SHALL let a user submit an answer for any rendered exercise,
display whether that specific attempt was correct or incorrect, and allow
the user to submit another answer for the same exercise without reloading
the page.

#### Scenario: Correct answer submitted
- **WHEN** a user submits the correct answer for an exercise
- **THEN** that exercise's card shows a correct indicator

#### Scenario: Incorrect answer submitted
- **WHEN** a user submits an incorrect answer for an exercise
- **THEN** that exercise's card shows an incorrect indicator, and the user
  can immediately submit a different answer for the same exercise

### Requirement: A correct exercise attempt updates the lesson's completion state
The frontend SHALL reflect a correct exercise attempt into the same
completion UI (success banner and progress display) already used by the
existing manual "Mark as complete" action, without introducing a separate
completion indicator.

#### Scenario: Correct attempt completes an incomplete lesson
- **WHEN** a user submits a correct answer for an exercise on a lesson that
  is not yet marked complete
- **THEN** the page re-fetches progress and shows the same completion
  banner the manual "Mark as complete" button produces

#### Scenario: Correct attempt on an already-complete lesson
- **WHEN** a user submits a correct answer for an exercise on a lesson that
  is already complete
- **THEN** the completion banner continues to display normally, with no
  duplicate or conflicting UI state

### Requirement: A failed exercise fetch is recoverable without reloading the page
The frontend SHALL show an error message and a manual retry action when
fetching a lesson's exercises fails, and SHALL retry the same fetch when
the user activates that action.

#### Scenario: Exercise fetch fails
- **WHEN** fetching a lesson's exercises fails (network error or a
  non-2xx response)
- **THEN** the page shows an error message with a retry button instead of
  the exercise cards

#### Scenario: Retry succeeds
- **WHEN** a user activates the retry button after a failed fetch
- **THEN** the frontend re-fetches the lesson's exercises and renders them
  normally if the retry succeeds
