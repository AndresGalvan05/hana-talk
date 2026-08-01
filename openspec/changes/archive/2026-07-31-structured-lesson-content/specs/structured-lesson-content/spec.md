## ADDED Requirements

### Requirement: Lesson content is structured, not a single flat string
Each lesson's content SHALL be composed of distinct sections: a list of
grammar points (each with a title, explanation, and example sentences), a
dialogue (a title and speaker-attributed lines), and a culture note (a
title and body) — served as structured data, not a single opaque text
field.

#### Scenario: A lesson's content is fetched
- **WHEN** `GET /api/courses/{courseId}/lessons` or
  `GET /api/courses/{courseId}/lessons/{lessonId}` is called
- **THEN** the response includes separately identifiable grammar points,
  dialogue, and culture note sections, not a single `content` string

#### Scenario: A lesson has multiple grammar points
- **WHEN** a lesson teaches more than one grammar rule
- **THEN** each rule is its own grammar point with its own explanation and
  example sentences, not concatenated into one block

### Requirement: Vocabulary is independently queryable per lesson
Each lesson's vocabulary SHALL be stored as individual, independently
retrievable items (Japanese term, reading, meaning), not embedded inside
unstructured lesson content.

#### Scenario: A lesson's vocabulary is fetched
- **WHEN** a lesson's vocabulary items are requested
- **THEN** each item is returned as a distinct record with its Japanese
  term, reading, and English meaning, addressable independently of the
  lesson's other content

### Requirement: Exercise generation scales with lesson content
Given a lesson's structured grammar points, exercise generation SHALL
produce more than a fixed two exercises, with output count reflecting how
many grammar points the lesson teaches.

#### Scenario: A lesson with several grammar points generates several exercises
- **WHEN** exercises are generated for a lesson with multiple grammar
  points, for the first time
- **THEN** the number of generated exercises is greater than two, and
  collectively exercises test more than one of the lesson's grammar points

#### Scenario: Every generated batch still includes at least one of each existing exercise type
- **WHEN** exercises are generated for any lesson
- **THEN** the batch includes at least one MCQ and at least one
  fill-in-the-blank exercise, regardless of how many total exercises are
  generated
