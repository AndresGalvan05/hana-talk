# conversation-practice Specification

## Purpose

Exposes conversation practice to authenticated users through core-api, the
single gateway the frontend talks to, deriving the difficulty level from
the caller's own profile rather than trusting a client-supplied value.

## Requirements

### Requirement: A chat reply endpoint is available to authenticated users only
The system SHALL expose `POST /api/conversation/reply`, accepting the
conversation history so far and a new message, requiring authentication,
and rejecting unauthenticated requests.

#### Scenario: Authenticated user sends a message
- **WHEN** an authenticated user posts conversation history and a new
  message to the endpoint
- **THEN** core-api calls `ai-exercise-svc` and returns the tutor's reply

#### Scenario: Unauthenticated request
- **WHEN** a request to the endpoint has no valid authentication
- **THEN** the system responds 401 and does not call `ai-exercise-svc`

### Requirement: The conversation's JLPT level is derived from the caller's profile
The system SHALL determine the JLPT level used for a chat reply from the
authenticated caller's own stored profile level, defaulting to N5 if the
caller has not set one, rather than accepting a level from the request
body.

#### Scenario: Caller has a set JLPT level
- **WHEN** an authenticated user with a stored JLPT level sends a chat
  message
- **THEN** core-api requests the reply at that stored level

#### Scenario: Caller has no JLPT level set
- **WHEN** an authenticated user with no stored JLPT level sends a chat
  message
- **THEN** core-api requests the reply at N5

### Requirement: A downstream chat failure surfaces as a gateway error
The system SHALL respond with a server error status if `ai-exercise-svc`'s
chat call fails for any reason (transport failure or exhausted provider
chain), rather than returning a partial or fabricated reply.

#### Scenario: ai-exercise-svc chat call fails
- **WHEN** the call to `ai-exercise-svc`'s chat endpoint fails
- **THEN** core-api responds with a server error status and no reply body
