## 1. Speech utility & component

- [ ] 1.1 New `frontend/src/lib/speech.ts`: `isSpeechSupported(): boolean`
      (`'speechSynthesis' in window`); `speak(text: string): void`
      (cancels any in-progress utterance, then speaks `text` with
      `lang: 'ja-JP'`, `rate: 0.85`)
- [ ] 1.2 New `frontend/src/components/SpeakButton.tsx`: `{ text: string
      }` prop, returns `null` if `!isSpeechSupported()`, otherwise a
      small button (🔊) that calls `speak(text)` on click, with an
      `aria-label` describing what it reads

## 2. Wiring into existing surfaces

- [ ] 2.1 `VocabularyTable.tsx`: render a `SpeakButton` next to each
      row's Japanese term
- [ ] 2.2 `GrammarPointCard.tsx`: render a `SpeakButton` next to each
      example sentence's Japanese text
- [ ] 2.3 `FlashcardsPage.tsx`: render a `SpeakButton` next to the
      current card's Japanese term, visible before and after reveal

## 3. Styling

- [ ] 3.1 New CSS in `frontend/src/index.css` for `SpeakButton` — small,
      unobtrusive, inline with the text it reads, not competing visually
      with the primary action buttons already on each page

## 4. Local verification

- [ ] 4.1 `docker compose up -d --build`, `npm run dev`; open a lesson
      page, click a vocabulary row's speaker button, confirm audio plays
      (or, in a headless/CI-like environment without audio output,
      confirm no console error and that `speechSynthesis.speaking`
      becomes `true` immediately after the click)
- [ ] 4.2 Click an example sentence's speaker button, confirm it plays
      independently of the vocabulary table
- [ ] 4.3 Open `/flashcards` with at least one due item, confirm the
      speaker button works both before and after revealing the card
- [ ] 4.4 Click two different speaker buttons in quick succession,
      confirm the second cancels the first rather than overlapping
      (observable via `speechSynthesis.speaking` staying `true`
      continuously across the switch, not toggling off/on)
- [ ] 4.5 `oxlint` and `tsc -b && vite build` both green

## 5. Production rollout

- [ ] 5.1 Deploy — merge to `main`, CI builds the frontend image,
      `kubectl rollout restart deployment/frontend` (no backend changes,
      no migration)
- [ ] 5.2 Spot-check the live site: click a speaker button on the
      vocabulary table and on a flashcard, confirm both play

## 6. Docs

- [ ] 6.1 Update `docs/DEVLOG.md` (session entry) and `docs/ROADMAP.md`
      decision log — note this completes all five originally-planned
      slices from the `structured-lesson-content` proposal
- [ ] 6.2 Update root `README.md`'s "Still ahead" list (move audio
      pronunciation to "Shipped so far" — this empties the "Still ahead"
      list; adjust the section framing accordingly rather than leaving
      an empty heading)
