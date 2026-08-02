## Context

`Layout.tsx` (31 lines, verbatim confirmed) renders the brand link, then
a plain `<nav>` (styled via the element selector `.site-header nav`,
`display:flex; align-items:center; gap:1rem`) containing Flashcards,
Chat practice, Achievements, a conditional Admin link, the username
link, and a Log out button — six to seven items with no wrap and no
mobile treatment. `.container` (`max-width: 780px; padding: 1.5rem 1rem
3rem`) already reflows content down to narrow widths on its own — the
nav row is the one piece that doesn't.

Confirmed via direct file reads: `.lesson-prev-next` is a fixed
`grid-template-columns: 1fr 1fr`; `.admin-list-row` and
`.leaderboard-row` are both `display:flex; gap:0.9rem` with no wrap;
`VocabularyTable.tsx`'s `<table>` has no scroll wrapper, just
`.vocabulary-table table { width: 100%; border-collapse: collapse; }`.
`.course-grid`/`.achievement-grid` already use
`grid-template-columns: repeat(auto-fill, minmax(...px, 1fr))`, which
already collapses to one column on narrow viewports — confirmed no fix
needed there.

## Goals / Non-Goals

**Goals:**
- Nothing overflows the viewport at a phone width (~375px).
- The nav becomes reachable via a hamburger + drawer below 640px,
  without changing its desktop appearance at all.
- Keep the diff small and targeted — one breakpoint, no new
  dependencies, no rewrite of unaffected components.

**Non-Goals:** see proposal.md — no dark mode, no design-token refactor,
no PWA work, no bottom tab bar, no new data visualization, no full
accessibility audit.

## Decisions

- **Single breakpoint, `max-width: 640px`,** applied consistently across
  every fix in this change, rather than a phone/tablet split. Simpler to
  reason about and verify; if tablet-width verification (~768px) shows
  the desktop nav row is actually tight, the fix is changing one number,
  not restructuring multiple breakpoints preemptively.
- **Drawer implementation stays inside `Layout.tsx` + CSS, no new
  component file.** The drawer is one more piece of state
  (`menuOpen: boolean`) and a few extra elements in the same component
  that already owns the nav — splitting it into a separate
  `MobileNav.tsx` would add an extra prop-passing layer (auth state,
  role, logout) for no real benefit at this size.
- **`.site-nav` stays mounted at all viewport widths, moved off-screen
  via `transform: translateX(100%)` rather than conditionally rendered.**
  This means the mobile-vs-desktop switch is pure CSS (`@media`), and
  only the open/closed *state* within mobile is JS-driven (the
  `site-nav-open` class toggle) — avoids a flash-of-unstyled-content
  problem where a conditionally-rendered drawer would need JS to know
  the current viewport width before deciding whether to render at all.
- **Renaming `.site-header nav` (element selector) to `.site-nav`
  (class)** is needed regardless of the drawer, because the mobile
  override needs to target the nav directly without also matching any
  future `<nav>` elsewhere in the app that isn't the site header's.
  Zero visual change at desktop widths — same declarations, just
  addressed by class instead of descendant-of-header.
- **Close-on-navigate via `useLocation().pathname` in a `useEffect`**,
  the same pattern `RequireAuth.tsx` already uses for reading route
  location in this codebase — no new pattern introduced.
- **Backdrop and drawer both render conditionally on `menuOpen`
  (backdrop) or are always-mounted-but-transformed (drawer)** — the
  backdrop only needs to intercept clicks while open, so conditional
  rendering is simpler there than for the drawer itself, which needs the
  transition to be visible.
- **`.admin-list-row`/`.leaderboard-row` get `flex-wrap: wrap` only** —
  not a restructure to `flex-direction: column` or a redesign of the row
  contents. The existing `flex:1` title/username element naturally
  claims the first line when wrapping, and the remaining action
  links/buttons flow to a second line. Minimal CSS change, no JSX
  change needed for either component.
- **Vocabulary table gets a `.table-scroll` wrapper `<div>`** around the
  existing `<table>` inside `VocabularyTable.tsx`'s `<section>` — the
  standard responsive-table pattern (`overflow-x: auto` on the wrapper),
  reusable if another table is ever added. `VocabularyTable`'s existing
  early-return (`if (items.length === 0) return null`) is unaffected —
  the wrapper only exists inside the branch that already renders.
- **Achievement lock/unlock icon is a one-line JSX change** in
  `AchievementsPage.tsx` (prefixing the title with `✅`/`🔒` based on
  `a.unlocked`), not a new component or new CSS — deliberately the
  smallest possible version of "visual polish" that's still a real,
  visible improvement (and doubles as a minor accessibility fix, since
  the previous locked/unlocked distinction was opacity-only).

## Risks / Trade-offs

- **[Risk] 640px might not be the right cutoff for every device in the
  wild** (e.g. some large phones in landscape, small tablets). Accepted
  — verification checks ~375px and ~768px specifically; if real-world
  feedback later shows a gap, adjusting one CSS value is cheap.
- **[Risk] No focus-trap or Escape-to-close on the drawer** (a full
  a11y-correct dialog pattern would add both). Accepted per the
  non-goals — this is a content nav drawer, not a modal form; backdrop-
  click-to-close and auto-close-on-navigate cover the common cases.
- **[Risk] `flex-wrap: wrap` on `.admin-list-row`/`.leaderboard-row` is
  a coarse fix** (rows just wrap wherever they run out of space, not a
  deliberately designed two-line layout). Accepted — this only affects
  the admin-only pages and the leaderboard, all lower-traffic than the
  core learning flow, and "wraps instead of overflowing" is a strict
  improvement over the current behavior either way.

## Migration Plan

None — frontend-only, no backend, no database, no new dependency.
Deploys through the existing frontend CI → GHCR → `kubectl rollout
restart` path, same as every prior frontend-only change.
