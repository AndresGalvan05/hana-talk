## 1. Speech utility & component

- [x] 1.1 New `frontend/src/lib/speech.ts`: `isSpeechSupported(): boolean`
      (`'speechSynthesis' in window`); `speak(text: string): void`
      (cancels any in-progress utterance, then speaks `text` with
      `lang: 'ja-JP'`, `rate: 0.85`)
- [x] 1.2 New `frontend/src/components/SpeakButton.tsx`: `{ text: string
      }` prop, returns `null` if `!isSpeechSupported()`, otherwise a
      small button (🔊) that calls `speak(text)` on click, with an
      `aria-label` describing what it reads

## 2. Wiring into existing surfaces

- [x] 2.1 `VocabularyTable.tsx`: render a `SpeakButton` next to each
      row's Japanese term
- [x] 2.2 `GrammarPointCard.tsx`: render a `SpeakButton` next to each
      example sentence's Japanese text
- [x] 2.3 `FlashcardsPage.tsx`: render a `SpeakButton` next to the
      current card's Japanese term, visible before and after reveal

## 3. Styling

- [x] 3.1 New CSS in `frontend/src/index.css` for `SpeakButton` — small,
      unobtrusive, inline with the text it reads, not competing visually
      with the primary action buttons already on each page

## 4. Local verification

- [x] 4.1 `docker compose up -d --build`, `npm run dev`; opened lesson 1,
      confirmed a 🔊 button renders next to every vocabulary row with a
      correct `aria-label`. This dev sandbox's Chrome instance has
      `'speechSynthesis' in window === true` but
      `speechSynthesis.getVoices().length === 0` (no TTS voices
      installed at the OS level) — a genuine environment limitation, not
      a code issue. Verified `speak()` is still correct at the API
      level: constructing and calling `speechSynthesis.speak()` directly
      throws no exception and the utterance carries the right
      `lang`/`rate`/`text`; the browser just has nothing installed to
      render it to audio. No console errors from the app's own code.
- [x] 4.2 Confirmed speaker buttons render next to all 7 example
      sentences across the lesson's grammar points, each with the
      correct sentence text in its `aria-label`
- [x] 4.3 Opened `/flashcards`; confirmed the speaker button is present
      on the unrevealed card and remains present, unchanged, after
      clicking Reveal
- [x] 4.4 Clicked two different vocabulary rows' speaker buttons in
      quick succession with `window.speechSynthesis.cancel` monkey-
      patched to count calls: confirmed exactly one `cancel()` call per
      click (2 clicks → 2 calls), i.e. every `speak()` invocation
      cancels first, matching the design
- [x] 4.5 `oxlint` and `tsc -b && vite build` both green

## 5. Production rollout

- [ ] 5.1 Deploy — merge to `main`, CI builds the frontend image,
      `kubectl rollout restart deployment/frontend` (no backend changes,
      no migration)
- [ ] 5.2 Spot-check the live site: click a speaker button on the
      vocabulary table and on a flashcard, confirm both play

## 6. Docs

- [x] 6.1 Updated `docs/DEVLOG.md` (session entry) and `docs/ROADMAP.md`
      decision log — noted this completes all five originally-planned
      slices from the `structured-lesson-content` proposal
- [x] 6.2 Restructured root `README.md`'s "What's next 🚧" section into
      "Deepening the content ✅" — the "Still ahead" list would otherwise
      be empty, so folded audio pronunciation into the single completed
      list rather than leaving a dangling empty heading; also updated the
      "Roadmap" section's cross-reference to the renamed heading
