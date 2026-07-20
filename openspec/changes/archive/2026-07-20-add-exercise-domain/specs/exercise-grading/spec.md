## ADDED Requirements

### Requirement: Lesson exercises are listed without their answers
The system SHALL expose the exercises attached to a lesson to any
authenticated user, and SHALL NOT include the correct answer in that
response.

#### Scenario: Fetch exercises for a lesson with exercises
- **WHEN** an authenticated user requests the exercises for a lesson that has
  seeded exercises
- **THEN** the response lists each exercise's id, type, prompt, and options
  (for MCQ), and contains no field revealing the correct answer

#### Scenario: Fetch exercises for a lesson with none
- **WHEN** an authenticated user requests the exercises for a lesson that has
  no exercises
- **THEN** the response is an empty list, not an error

### Requirement: Exercise attempts are graded synchronously against a stored answer
The system SHALL grade a submitted answer by exact comparison (case- and
whitespace-normalized for fill-in-blank; verbatim for MCQ) against the
exercise's stored correct answer, without calling any external service, and
SHALL persist every attempt regardless of outcome.

#### Scenario: Correct MCQ attempt
- **WHEN** a user submits the exact text of the correct option for an MCQ
  exercise
- **THEN** the response reports the attempt as correct, and an
  `ExerciseAttempt` record is persisted with `isCorrect = true`

#### Scenario: Correct fill-in-blank attempt with different casing/whitespace
- **WHEN** a user submits an answer that matches the stored answer after
  trimming and lowercasing
- **THEN** the response reports the attempt as correct

#### Scenario: Incorrect attempt
- **WHEN** a user submits an answer that does not match the stored answer
- **THEN** the response reports the attempt as incorrect, and an
  `ExerciseAttempt` record is persisted with `isCorrect = false`, and no
  lesson completion is triggered

#### Scenario: Attempt on a nonexistent exercise
- **WHEN** a user submits an attempt for an exercise id that does not exist
- **THEN** the system responds 404 and persists no attempt

### Requirement: A correct attempt completes the lesson via the existing progress mechanism
The system SHALL mark the exercise's lesson complete through the same
mechanism used by manual completion, using `CompletionSource.EXERCISE`, when
a submitted attempt is graded correct — reusing the existing
`user_lesson_progress` record and `exercise.completed` Kafka event rather
than introducing a parallel completion path.

#### Scenario: First correct attempt on an incomplete lesson
- **WHEN** a user submits a correct answer for an exercise whose lesson is
  not yet marked complete for that user
- **THEN** a `user_lesson_progress` row is created with
  `source = EXERCISE`, and an `exercise.completed` event is published with
  `source = "EXERCISE"`

#### Scenario: Correct attempt on an already-complete lesson
- **WHEN** a user submits a correct answer for an exercise whose lesson is
  already marked complete for that user (via a prior manual completion or a
  prior correct attempt)
- **THEN** the attempt is still recorded and reported correct, and no
  duplicate `user_lesson_progress` row or `exercise.completed` event is
  created

#### Scenario: Incorrect attempt never completes the lesson
- **WHEN** a user submits an incorrect answer
- **THEN** no `user_lesson_progress` row is created and no
  `exercise.completed` event is published as a result of that attempt
