## Why

Every lesson shows Japanese text — vocabulary, example sentences, flashcards
— but a learner has no way to hear it. This was deliberately sequenced
last in the deepening-interactivity plan specifically because it's cheap
to build once there's a vocabulary table and a flashcard page to attach it
to (both now shipped): entirely client-side via the browser's built-in Web
Speech API, no backend, no LLM call, no new infrastructure.

## What Changes

- A small reusable `SpeakButton` component (🔊 icon) that calls
  `window.speechSynthesis.speak(...)` with `lang: 'ja-JP'` and a slightly
  slower-than-default rate (0.85), since this is a learning aid, not a
  screen reader.
- Wired into the three places the original slice plan named: the
  vocabulary table (per row), grammar-point example sentences (per
  example), and the flashcard page (the current card's Japanese term).
- Feature-detects `window.speechSynthesis` and renders nothing if it's
  unavailable, rather than a disabled/broken-looking button.
- No backend changes of any kind.

## Capabilities

### New Capabilities
- `audio-pronunciation-ui`: the speaker-button component and its
  integration into the three existing display surfaces.

### Modified Capabilities
(none)

## Impact

- `frontend/`: new `SpeakButton.tsx` and a small `speech.ts` utility;
  edits to `VocabularyTable.tsx`, `GrammarPointCard.tsx`, and
  `FlashcardsPage.tsx` to render it.
- No changes to `core-api`, `ai-exercise-svc`, `event-worker`, or any
  database.

## Non-goals / cut line

- No speed/voice selection UI — a fixed rate, browser-default Japanese
  voice. If the OS/browser has no Japanese voice installed, the browser's
  own fallback behavior applies (typically an accented read using
  whatever voice is available) — not specifically detected or warned
  about, since the Web Speech API gives no reliable way to check voice
  availability before attempting playback.
- No dialogue-line or chat-reply audio — scoped to exactly the three
  surfaces named in the original plan (vocabulary, grammar examples,
  flashcards). Cheap to extend later if it turns out to matter, since
  it's the same reusable component either way.
- No audio caching, pre-recorded audio files, or a TTS backend service —
  purely client-side Web Speech API, by design (see Why).
- No autoplay — every reading is manually triggered by clicking the
  speaker icon.

## Milestone

Post-roadmap slice (M1–M5 complete). Slice 5 — the last slice — of the
deepening-interactivity plan from `structured-lesson-content`'s proposal.
Slices 1–4 (`structured-lesson-content`, `new-exercise-types`,
`ai-conversation-practice`, `vocabulary-review`) and the interleaved
`profile-and-progress` slice are already shipped and archived.
