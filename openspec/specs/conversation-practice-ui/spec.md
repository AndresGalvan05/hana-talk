# conversation-practice-ui Specification

## Purpose

Gives learners a page to hold a free-form Japanese conversation with an
LLM tutor and see corrections, the first practice surface in the app that
isn't a closed-form exercise type.

## Requirements

### Requirement: The chat page lets a user send a message and see the tutor's reply
The frontend SHALL provide a chat page with a text input for the user's
Japanese message and a running transcript, appending the user's message
and the tutor's reply (Japanese and English) to the transcript after each
exchange.

#### Scenario: Sending the first message
- **WHEN** a user types a message and sends it on an empty chat page
- **THEN** the transcript shows the user's message followed by the
  tutor's reply once it arrives

#### Scenario: Sending a follow-up message
- **WHEN** a user sends another message after at least one prior exchange
- **THEN** the new exchange is appended below the existing transcript,
  which remains visible

#### Scenario: Tutor reply is pending
- **WHEN** a user has sent a message and the tutor's reply has not yet
  arrived
- **THEN** the page shows a loading indicator and the input is disabled
  until the reply arrives or the request fails

### Requirement: A correction is shown distinctly from the tutor's conversational reply
The frontend SHALL visually distinguish a correction of the user's
message, when one is present in the tutor's response, from the tutor's
conversational reply.

#### Scenario: Reply includes a correction
- **WHEN** the tutor's reply includes a correction of the user's last
  message
- **THEN** the correction is rendered separately from the conversational
  reply, so the two are not confused

#### Scenario: Reply has no correction
- **WHEN** the tutor's reply has no correction
- **THEN** no correction element is rendered for that exchange

### Requirement: A failed reply is shown as a recoverable error, not a stuck state
The frontend SHALL show an error message and allow the user to retry
sending their message if the reply request fails, without losing the
message they typed.

#### Scenario: Reply request fails
- **WHEN** a request to get the tutor's reply fails
- **THEN** the page shows an error and lets the user retry, and the
  transcript is not left in a permanently-loading state

### Requirement: The chat page is reachable from site navigation
The frontend SHALL provide a link to the chat page from the site
navigation, visible to any authenticated user.

#### Scenario: Navigating to chat
- **WHEN** an authenticated user is on any page
- **THEN** the site navigation shows a link to the chat page
