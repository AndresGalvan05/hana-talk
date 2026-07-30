## Why

Every practice mode so far is closed-form (MCQ, fill-in-blank, translation,
sentence-ordering) — the learner never produces free Japanese and gets
feedback on it, which was an explicit, named cut line at M3 ("LLM-graded
free text" was cut). This slice finally builds that: a chat page where a
learner converses with an LLM tutor in Japanese and gets corrections,
reusing `ai-exercise-svc`'s existing Gemini → Groq → OpenRouter failover
chain for a new, free-form purpose instead of exercise generation.

## What Changes

- New `ai-exercise-svc` endpoint `POST /chat`: takes the conversation so
  far plus the learner's new message, returns the tutor's next line
  (Japanese + English translation) and, if the learner's last message had
  a mistake, a short correction. Uses the same structured-output/schema-
  validated call pattern `/generate` already uses (not free-text
  completions) — the reply shape mirrors `LessonContent`'s existing
  `DialogueLine` (`japanese`/`english`), so the same provider machinery
  and validation discipline extends to a genuinely different feature
  instead of introducing a second calling convention.
- Provider fallback logic (`try Gemini, then Groq, then OpenRouter,
  falling through on any failure`) is factored out of `generate_exercises`
  into a small shared helper, used by both exercise generation and chat —
  this was previously inlined in `generation.py`, now the pattern exists
  in two places.
- New core-api endpoint `POST /api/conversation/reply`: authenticated, thin
  proxy to `ai-exercise-svc`. Derives the JLPT level from the caller's own
  profile (`User.startingLevel`, defaulting to N5 if unset — the same
  field the `profile-and-progress` slice just made editable) rather than
  trusting a client-supplied level.
- New frontend `/chat` page and a nav link to it. Conversation history
  lives only in React state for that page session — no new database table,
  no cross-session persistence. Refreshing the page starts a new
  conversation.

## Capabilities

### New Capabilities
- `conversation-generation`: `ai-exercise-svc`'s chat-reply contract —
  provider fallback, schema validation, JLPT-level-aware prompting.
  Mirrors `exercise-generation`'s scope for the new feature.
- `conversation-practice`: core-api's `/api/conversation/reply` endpoint —
  authentication requirement and JLPT-level derivation from the caller's
  profile. Mirrors `exercise-grading`'s role as the core-api-side contract,
  scoped down since there's no grading or persistence here.
- `conversation-practice-ui`: the frontend chat page. Mirrors
  `exercise-practice-ui`'s role for the new feature.

### Modified Capabilities
(none)

## Impact

- `ai-exercise-svc/`: new `app/chat.py` (reply generation, mirroring
  `generation.py`), a new shared fallback helper extracted from
  `generation.py`, new `ChatMessage`/`ChatRequest`/`ChatReply` schemas in
  `app/schemas.py`, a new route in `app/routes.py`.
- `core-api/`: new `ConversationController`, a new method on the existing
  `AiExerciseSvcClient` (not a new client bean — both `/generate` and
  `/chat` target the same downstream service, and this codebase's
  convention is one client per downstream service, not per endpoint; see
  `EventWorkerClient` already handling two endpoints).
- `frontend/`: new `ChatPage.tsx`, a new `/chat` route, a nav link in
  `Layout.tsx`.
- No database migration in any service — chat has no persisted state.
- No Kafka involvement — chat does not publish `exercise.completed` or
  otherwise affect streaks/leaderboard (see non-goals below).

## Non-goals / cut line

- No conversation persistence across page reloads or sessions — explicitly
  ephemeral, frontend-state-only, per the original slice plan. A
  `conversations` table is deferred to a future slice if this turns out to
  matter.
- No tie-in to lessons/courses — this is a standalone practice surface
  reachable from site navigation, not scoped to a specific lesson's
  grammar points.
- No streak/leaderboard/Kafka integration — a chat turn does not publish
  `exercise.completed` or otherwise count toward gamification. Keeps this
  slice's scope to the conversation feature itself; revisit later if
  chat activity should count toward streaks.
- No voice/audio input or output — that's the separate, already-planned
  audio-pronunciation slice.
- No rate-limiting or abuse prevention beyond what already exists
  elsewhere in the app (i.e. none) — consistent with every other
  LLM-calling endpoint today, not a new gap this slice introduces.

## Milestone

Post-roadmap slice (M1–M5 complete). Slice 3 of the deepening-interactivity
plan from `structured-lesson-content`'s proposal — the first two slices
(`structured-lesson-content`, `new-exercise-types`) and the
`profile-and-progress` slice (proposed after the plan, in response to a
Kafka-usage audit) are already shipped and archived.
