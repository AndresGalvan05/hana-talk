# conversation-generation Specification

## Purpose

Generates the tutor's next line in a Japanese conversation practice
session — a reply plus an optional correction of the learner's last
message — using the same multi-provider failover and strict schema
validation `ai-exercise-svc` already applies to exercise generation.

## Requirements

### Requirement: A chat reply is generated from conversation history and a new message
`ai-exercise-svc` SHALL generate a tutor reply given the conversation so
far, the learner's new message, and a JLPT level, producing a reply in
Japanese with an English translation, and an optional correction of the
learner's message if it contained a Japanese-language mistake.

#### Scenario: Learner sends a message with no mistake
- **WHEN** a chat request is received with a learner message that contains
  no Japanese-language error
- **THEN** the response includes a Japanese reply and its English
  translation continuing the conversation, and no correction

#### Scenario: Learner sends a message with a mistake
- **WHEN** a chat request is received with a learner message that contains
  a Japanese-language error (grammar, particle, or word choice)
- **THEN** the response includes a short correction describing the
  mistake, alongside the reply continuing the conversation

#### Scenario: First message of a conversation
- **WHEN** a chat request is received with empty conversation history
- **THEN** `ai-exercise-svc` still generates an opening reply, without
  requiring any prior turns

### Requirement: Chat replies are validated against a strict JSON schema
`ai-exercise-svc` SHALL validate a provider's chat response against a
schema requiring a non-empty Japanese reply and a non-empty English
translation, and SHALL reject a non-conforming response rather than
returning it.

#### Scenario: Provider returns a schema-conforming reply
- **WHEN** a provider's response has a non-empty Japanese reply and
  English translation
- **THEN** `ai-exercise-svc` returns it as the chat reply

#### Scenario: Provider returns a malformed reply
- **WHEN** a provider's response is missing the Japanese reply or the
  English translation
- **THEN** `ai-exercise-svc` does not return that response, and instead
  attempts the next provider in the chain

### Requirement: A provider failure falls through to the next provider in the chain
`ai-exercise-svc` SHALL attempt providers in the same fixed order used for
exercise generation — Gemini, then Groq, then OpenRouter — for a chat
request, moving to the next provider immediately when the current one
fails (transport error, timeout, or schema-validation failure).

#### Scenario: Primary provider fails, fallback succeeds
- **WHEN** Gemini fails for a chat request
- **THEN** `ai-exercise-svc` attempts Groq next with the same prompt

#### Scenario: Every provider in the chain fails
- **WHEN** all providers fail for the same chat request
- **THEN** `ai-exercise-svc` responds with an error status and no reply is
  returned

### Requirement: Conversation history is bounded before being sent to a provider
`ai-exercise-svc` SHALL include at most the most recent 20 messages of
conversation history in the prompt sent to a provider, regardless of how
much history the caller supplies, to keep prompt size bounded.

#### Scenario: History longer than the bound
- **WHEN** a chat request includes more than 20 prior messages
- **THEN** only the most recent 20 are included in the prompt sent to the
  provider
