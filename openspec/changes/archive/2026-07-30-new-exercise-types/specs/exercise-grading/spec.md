## MODIFIED Requirements

### Requirement: Lesson exercises are listed without their answers
The system SHALL expose the exercises attached to a lesson to any
authenticated user, and SHALL NOT include the correct answer in that
response.

#### Scenario: Fetch exercises for a lesson with exercises
- **WHEN** an authenticated user requests the exercises for a lesson that has
  seeded exercises
- **THEN** the response lists each exercise's id, type, prompt, and options
  (for MCQ and SENTENCE_ORDERING), and contains no field revealing the
  correct answer

#### Scenario: Fetch exercises for a lesson with none
- **WHEN** an authenticated user requests the exercises for a lesson that has
  no exercises
- **THEN** the response is an empty list, not an error

### Requirement: Exercise attempts are graded synchronously against a stored answer
The system SHALL grade a submitted answer by exact comparison against the
exercise's stored correct answer, without calling any external service, and
SHALL persist every attempt regardless of outcome. Comparison is
case- and whitespace-normalized for FILL_IN_BLANK and TRANSLATION; verbatim
for MCQ; whitespace-normalized but case-sensitive for SENTENCE_ORDERING
(word order and exact tokens both matter).

#### Scenario: Correct MCQ attempt
- **WHEN** a user submits the exact text of the correct option for an MCQ
  exercise
- **THEN** the response reports the attempt as correct, and an
  `ExerciseAttempt` record is persisted with `isCorrect = true`

#### Scenario: Correct fill-in-blank attempt with different casing/whitespace
- **WHEN** a user submits an answer that matches the stored answer after
  trimming and lowercasing
- **THEN** the response reports the attempt as correct

#### Scenario: Correct translation attempt with different casing/whitespace
- **WHEN** a user submits a translation answer that matches the stored
  answer after trimming and lowercasing
- **THEN** the response reports the attempt as correct

#### Scenario: Correct sentence-ordering attempt
- **WHEN** a user submits the exercise's tokens joined by single spaces in
  the exact stored order
- **THEN** the response reports the attempt as correct

#### Scenario: Sentence-ordering attempt with the right tokens in the wrong order
- **WHEN** a user submits the exercise's tokens joined by single spaces in
  any order other than the stored order
- **THEN** the response reports the attempt as incorrect

#### Scenario: Incorrect attempt
- **WHEN** a user submits an answer that does not match the stored answer
- **THEN** the response reports the attempt as incorrect, and an
  `ExerciseAttempt` record is persisted with `isCorrect = false`, and no
  lesson completion is triggered

#### Scenario: Attempt on a nonexistent exercise
- **WHEN** a user submits an attempt for an exercise id that does not exist
- **THEN** the system responds 404 and persists no attempt
