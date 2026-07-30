## Purpose

Gives learners a flashcard page to work through their daily vocabulary
review queue, one item at a time, without needing to know anything about
the underlying scheduling.

## ADDED Requirements

### Requirement: The flashcards page shows one due item at a time, front before back
The frontend SHALL fetch the user's due review queue and present one
vocabulary item at a time, initially showing only the Japanese term, and
revealing the reading and meaning only after the user requests it.

#### Scenario: Card starts unrevealed
- **WHEN** a user opens the flashcards page with items due
- **THEN** the first card shows only the Japanese term, not the reading
  or meaning

#### Scenario: Revealing a card
- **WHEN** a user requests to reveal the current card
- **THEN** the reading and meaning are shown alongside the Japanese term

### Requirement: Marking a card correct or incorrect advances to the next due item
The frontend SHALL let the user mark the revealed card as correct or
incorrect, submit that result, and advance to the next item in the queue
without a page reload.

#### Scenario: Marking correct advances the queue
- **WHEN** a user marks a revealed card correct
- **THEN** the result is submitted and the next due card is shown,
  unrevealed

#### Scenario: Marking incorrect advances the queue
- **WHEN** a user marks a revealed card incorrect
- **THEN** the result is submitted and the next due card is shown,
  unrevealed

### Requirement: An empty queue is shown as a clear, distinct state
The frontend SHALL show an explicit "nothing due" message when the
review queue is empty, rather than an empty or blank card area.

#### Scenario: No items due
- **WHEN** a user opens the flashcards page with no items due
- **THEN** the page shows a message indicating there's nothing to review
  right now, not a blank state

#### Scenario: Queue exhausted during a session
- **WHEN** a user marks the last card in their queue
- **THEN** the page shows the same "nothing due" message rather than an
  error or blank card

### Requirement: The flashcards page is reachable from site navigation
The frontend SHALL provide a link to the flashcards page from the site
navigation, visible to any authenticated user.

#### Scenario: Navigating to flashcards
- **WHEN** an authenticated user is on any page
- **THEN** the site navigation shows a link to the flashcards page
