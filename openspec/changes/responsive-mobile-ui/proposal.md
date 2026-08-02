## Why

`frontend/src/index.css` (~755 lines) has zero `@media` queries anywhere,
and no page has been verified at a mobile viewport this session — a full
grep of the CSS and of `docs/DEVLOG.md`/`docs/ROADMAP.md` confirms no
prior mobile work or stated non-goal exists. The header nav
(`Layout.tsx`) has grown to up to 7 items across the feature slices
shipped this session (Flashcards, Chat practice, Achievements,
conditionally Admin, username, Log out, plus the brand link) in a plain
`display:flex` row with no wrap — on a phone-width viewport this
overflows the header instead of reflowing. A few other components have
the same class of problem, never designed against a narrow viewport
because none was ever tested against one.

## What Changes

- A hamburger button + slide-out drawer for the header nav below a
  single mobile breakpoint (`max-width: 640px`), closing automatically
  on navigation or backdrop click.
- Mobile-safe layout for `.lesson-prev-next` (stacks to one column),
  `.admin-list-row`/`.leaderboard-row` (wrap instead of overflowing),
  and the vocabulary table (wrapped in a horizontal-scroll container).
- A small visual-polish item: achievement cards get a ✅/🔒 prefix on
  the title so locked/unlocked status doesn't rely on opacity alone.

## Capabilities

### New Capabilities
- `responsive-mobile-ui`: the mobile nav drawer and the mobile-safe
  layout behavior of the affected components.

### Modified Capabilities
(none — this changes presentation/layout, not any existing functional
requirement)

## Impact

- `frontend/src/components/Layout.tsx`: hamburger button, drawer open/
  close state, close-on-navigate, backdrop.
- `frontend/src/index.css`: rename `.site-header nav` → `.site-nav`;
  new `.nav-toggle`/`.nav-backdrop` rules; one new
  `@media (max-width: 640px)` block covering the drawer and the other
  component fixes.
- `frontend/src/components/VocabularyTable.tsx`: wraps its `<table>` in
  a `.table-scroll` container.
- `frontend/src/pages/AchievementsPage.tsx`: adds the lock/unlock icon
  prefix.
- No backend changes, no new dependency (no icon library, no CSS
  framework), no database migration.

## Non-goals / cut line

- No dark mode, no design-token/spacing-scale refactor — targeted fixes
  only, not a redesign.
- No PWA/installability work (manifest, service worker).
- No bottom tab bar — a hamburger + drawer was chosen over an app-style
  bottom nav.
- No new charts/graphs or other net-new data visualization — the
  "visualization" part of this request is general visual polish on
  existing pages, not new visual elements.
- No changes to the admin lesson JSON textarea beyond what it already
  gets for free (`width: 100%` already applies at any viewport).
- No full accessibility audit — only `aria-label`/`aria-expanded` on the
  hamburger button.
- Single breakpoint (640px), not a phone/tablet split — `.container`'s
  existing 780px max-width plus short nav-link text likely means
  tablet-width viewports already fit the desktop nav row; only proven
  otherwise during verification, not pre-emptively split into two
  breakpoints.

## Milestone

Post-roadmap, fourth slice since the roadmap completed — the first one
driven by direct user feedback on an existing gap (mobile support) rather
than a pre-planned "close a known gap" item from the earlier post-roadmap
planning session (rate limiting, achievements, admin UI). Extended
observability (`ai-exercise-svc`/`event-worker` tracing) remains the one
item still on that original list.
