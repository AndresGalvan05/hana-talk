# chat-rate-limiting Specification

## Purpose

Protects the chat endpoint — the app's one unbounded, per-request,
real-money LLM cost driver — from a single user sending requests faster
than a real conversation would, without adding new infrastructure.

## Requirements

### Requirement: Chat replies are capped at a fixed rate per authenticated user
The system SHALL limit each authenticated user to at most 10 requests to
`POST /api/conversation/reply` per rolling one-minute window, and SHALL
respond `429 Too Many Requests` (without calling `ai-exercise-svc`) to any
request beyond that limit within the current window.

#### Scenario: Requests within the limit succeed normally
- **WHEN** an authenticated user has made fewer than 10 requests to the
  chat endpoint within the current one-minute window
- **THEN** the request proceeds normally and calls `ai-exercise-svc`

#### Scenario: The 11th request within a window is rejected
- **WHEN** an authenticated user makes an 11th request to the chat
  endpoint within the same rolling one-minute window
- **THEN** the system responds `429 Too Many Requests` and does not call
  `ai-exercise-svc`

#### Scenario: The limit resets after the window elapses
- **WHEN** a user who has exhausted their limit waits until the one-minute
  window has elapsed and sends another request
- **THEN** that request is allowed and counts as the first request of a
  new window

#### Scenario: Limits are tracked independently per user
- **WHEN** two different authenticated users each send requests to the
  chat endpoint
- **THEN** one user reaching their limit does not affect the other user's
  remaining allowance

### Requirement: The frontend distinguishes a rate-limit response from other failures
The frontend SHALL show a message specifically indicating the user is
sending messages too quickly when a chat reply request fails with a 429
response, rather than the generic failure message used for other errors,
and SHALL still preserve the user's typed message for retry exactly as it
already does for any other failure.

#### Scenario: A chat request is rate-limited
- **WHEN** a request to the chat reply endpoint fails with a 429 response
- **THEN** the page shows a message indicating the user should slow down,
  and the message they typed remains available to resend

#### Scenario: A non-rate-limit failure still shows the generic message
- **WHEN** a request to the chat reply endpoint fails for a reason other
  than a 429 response
- **THEN** the page shows its existing generic "could not get a reply"
  message
