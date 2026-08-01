# audio-pronunciation-ui Specification

## Purpose

Lets a learner hear the pronunciation of any Japanese text shown in
vocabulary lists, grammar examples, and flashcards, using the browser's
own text-to-speech rather than a new backend service.

## Requirements

### Requirement: A speaker button reads Japanese text aloud when clicked
The frontend SHALL provide a speaker-icon control next to Japanese text in
the vocabulary table, grammar-point example sentences, and the flashcard
page, which plays that text aloud in Japanese via the browser's speech
synthesis API when clicked.

#### Scenario: Clicking a vocabulary item's speaker button
- **WHEN** a user clicks the speaker button next to a vocabulary table row
- **THEN** that row's Japanese term is read aloud in Japanese

#### Scenario: Clicking an example sentence's speaker button
- **WHEN** a user clicks the speaker button next to a grammar point's
  example sentence
- **THEN** that sentence's Japanese text is read aloud in Japanese

#### Scenario: Clicking a flashcard's speaker button
- **WHEN** a user clicks the speaker button on the current flashcard
- **THEN** the card's Japanese term is read aloud in Japanese, whether or
  not the card has been revealed yet

### Requirement: Starting a new reading stops any reading in progress
The frontend SHALL cancel any in-progress speech before starting a new
one, so overlapping audio never plays.

#### Scenario: Clicking a second speaker button while one is still playing
- **WHEN** a user clicks a speaker button while a previous reading has not
  finished
- **THEN** the previous reading stops immediately and the new text begins
  playing

### Requirement: The speaker button is absent when speech synthesis is unavailable
The frontend SHALL not render a speaker button on a page load where the
browser's speech synthesis API is unavailable, rather than showing a
control that does nothing or errors when clicked.

#### Scenario: Speech synthesis is unavailable
- **WHEN** the browser does not support the speech synthesis API
- **THEN** no speaker buttons are rendered anywhere they would otherwise
  appear
