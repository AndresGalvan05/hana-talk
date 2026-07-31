## Context

Three existing components display standalone pieces of Japanese text that
this slice needs a speaker button next to: `VocabularyTable.tsx` (one row
per vocabulary item, `item.japanese`), `GrammarPointCard.tsx` (one `<li>`
per example sentence, `example.japanese`), and `FlashcardsPage.tsx` (the
current card's `current.japanese`, shown whether or not the card is
revealed). All three already render Japanese text with `lang="ja"` on the
containing element. No component in the codebase currently touches
`window.speechSynthesis` — this is a new browser API surface for the
project.

## Goals / Non-Goals

**Goals:**
- One small, reusable component and utility function, not three separate
  implementations.
- Feel instant and safe to mash — clicking while something is already
  playing should just switch to the new text, not queue or error.

**Non-Goals:**
- See proposal.md's cut line — no voice/speed UI, no dialogue/chat
  coverage, no caching or backend TTS.

## Decisions

- **A single `speak(text: string)` utility in
  `frontend/src/lib/speech.ts`**, used by one `SpeakButton.tsx` component
  (`{ text: string }` prop), rather than inlining
  `window.speechSynthesis` calls in three different components. The
  utility calls `window.speechSynthesis.cancel()` before
  `window.speechSynthesis.speak(...)` every time — cancel-then-speak,
  not queue — so rapid clicking always plays the most recently clicked
  text rather than queuing up a backlog of stale audio.
- **Rate fixed at 0.85, not exposed as a setting.** A language-learning
  app benefits from slightly slower-than-natural speech by default; a
  full speed control is unnecessary complexity for what this slice is
  actually for (hearing pronunciation on demand, not a general-purpose
  reader).
- **Feature detection happens once, in the utility module, not per-call.**
  `SpeakButton` checks `'speechSynthesis' in window` at render time and
  returns `null` if it's absent — simpler than a `disabled` visual state,
  and matches the requirement that an unsupported browser sees no button
  at all rather than a non-functional one.
- **No loading/playing visual state on the button itself.** The Web
  Speech API's `onstart`/`onend` events would let a button show a
  "playing" state, but for very short single-word/single-sentence
  utterances this adds UI complexity disproportionate to the benefit —
  cut for this slice, revisit if longer text (e.g. a full dialogue) is
  ever added to scope.

## Risks / Trade-offs

- **[Risk] No Japanese voice installed on a given OS/browser** →
  Accepted per proposal's non-goals; the Web Speech API doesn't expose a
  reliable pre-flight check, and the browser's own fallback (typically an
  accented read with whatever voice is available) is still better than no
  audio at all.
- **[Risk] `speechSynthesis.getVoices()` can return an empty list on
  first call in some browsers until a `voiceschanged` event fires** →
  Not a blocker here since `speak()` doesn't need to enumerate voices
  first — it just calls `speak()` with a `lang` hint and lets the browser
  pick, so this quirk (which matters for voice-selection UIs) doesn't
  apply to this design.

## Migration Plan

None — no backend, no database, no new dependency (the Web Speech API is
a browser built-in, nothing to add to `package.json`). Ships through the
existing frontend-only CI → GHCR → `kubectl rollout restart
deployment/frontend` path.
