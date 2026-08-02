## 1. Nav rename + base drawer scaffolding

- [x] 1.1 `index.css`: rename the `.site-header nav` selector to
      `.site-nav` (identical declarations, just class-addressed instead
      of element-descendant-addressed) — zero visual change at desktop
      widths
- [x] 1.2 `index.css`: new `.nav-toggle` rule (`display: none` by
      default, hamburger button styling reusing `.link-button`-style
      "no background" treatment) and `.nav-backdrop` rule
      (`display: none` by default)

## 2. Hamburger + drawer behavior

- [x] 2.1 `Layout.tsx`: add `menuOpen` state (`useState(false)`); add
      `<button className="nav-toggle" aria-label="Menu"
      aria-expanded={menuOpen} onClick={...}>☰</button>` between the
      brand link and `<nav>`
- [x] 2.2 `Layout.tsx`: `<nav>` gains `className={menuOpen ? 'site-nav
      site-nav-open' : 'site-nav'}`; add
      `{menuOpen && <div className="nav-backdrop" onClick={() =>
      setMenuOpen(false)} />}` immediately before the nav
- [x] 2.3 `Layout.tsx`: `useEffect` keyed on `useLocation().pathname`
      (from `react-router-dom`, same import already used in
      `RequireAuth.tsx`) that calls `setMenuOpen(false)` on every route
      change

## 3. Mobile CSS block

- [x] 3.1 `index.css`: new `@media (max-width: 640px) { ... }` block
      containing:
      - `.nav-toggle { display: block; }`
      - `.site-nav` override: `position: fixed; top: 0; right: 0;
        height: 100vh; width: min(75vw, 280px); background:
        var(--surface); border-left: 1px solid var(--border);
        flex-direction: column; align-items: flex-start; gap: 1.2rem;
        padding: 4rem 1.5rem 1.5rem; transform: translateX(100%);
        transition: transform 0.2s ease; z-index: 20;`
      - `.site-nav-open { transform: translateX(0); }`
      - `.nav-backdrop { display: block; position: fixed; inset: 0;
        background: rgba(0, 0, 0, 0.35); z-index: 10; }`
      - `.site-nav a, .site-nav button { padding: 0.4rem 0; }` (tap
        target bump, mobile-drawer-only)
      - `.lesson-prev-next { grid-template-columns: 1fr; }`
      - `.admin-list-row { flex-wrap: wrap; }`
      - `.leaderboard-row { flex-wrap: wrap; }`

## 4. Vocabulary table scroll wrapper

- [x] 4.1 `VocabularyTable.tsx`: wrap the existing `<table>` in
      `<div className="table-scroll">...</div>`, inside the existing
      `<section className="vocabulary-table">` (no change to the
      early-return-`null` branch)
- [x] 4.2 `index.css`: new `.table-scroll { overflow-x: auto; }` rule
      (outside the media block — applies at any width, harmless when
      the table already fits)

## 5. Achievement lock/unlock icon

- [x] 5.1 `AchievementsPage.tsx`: prefix each card's
      `<p className="achievement-title">` content with `✅ ` (unlocked)
      or `🔒 ` (locked) based on `a.unlocked`

## 6. Lint & build

- [x] 6.1 `oxlint` green
- [x] 6.2 `tsc -b && vite build` green

## 7. Local verification

- [x] 7.1 `docker compose up -d --build` (in `infra/`), `npm run dev`
      (in `frontend/`). Note: the environment's `resize_window` browser
      tool did not actually resize the OS window (likely the tiling
      window manager), so verification used a same-origin `<iframe>`
      with a fixed CSS pixel width instead — this correctly triggers
      real `@media` query evaluation (confirmed via
      `matchMedia('(max-width: 640px)').matches` inside the iframe),
      unlike a scaled screenshot would.
- [x] 7.2 At ~375px viewport width: confirm the hamburger is visible and
      the nav row is not; open the drawer and confirm it slides in from
      the right with a backdrop; click a drawer link and confirm it
      both navigates and closes the drawer; reopen the drawer and click
      the backdrop, confirming it closes without navigating. Verified
      all of the above directly against DOM/class state (not just
      screenshots, since the iframe screenshot occasionally lagged a
      render frame): hamburger shown, nav row hidden;
      opening sets `site-nav-open` + mounts `.nav-backdrop`; clicking a
      link navigated (`pathname` changed) and removed `site-nav-open`;
      clicking the backdrop removed `site-nav-open` and unmounted the
      backdrop with no `pathname` change. Also confirmed no "Admin" link
      renders for a non-admin user (role gating still correct on
      mobile).
- [x] 7.3 At ~375px: open a lesson with both a previous and next lesson
      and confirm the prev/next links stack in one column; view the
      admin course/lesson list as an admin (reuse the existing
      manual-DB-grant pattern) and confirm action buttons wrap onto a
      second line instead of overflowing; view a lesson with vocabulary
      and confirm the table renders inside its scroll container without
      breaking page width; view the achievements page and confirm the
      ✅/🔒 prefix shows correctly for both locked and unlocked cards.
      **Found and fixed a real bug during this check**: the initial
      `.lesson-prev-next { grid-template-columns: 1fr; }` override alone
      did not stack the layout — computed style still showed two
      columns. Root cause: `.lesson-nav-next { grid-column: 2; }`
      (unrelated existing rule, sets the "next" link's explicit grid
      position) forced CSS Grid to auto-generate an implicit second
      column to satisfy that explicit placement, even though the
      container's explicit template was down to one track. Fixed by
      also adding `.lesson-nav-next { grid-column: auto; }` inside the
      same mobile media block. Re-verified: computed
      `grid-template-columns` is a single `328px` track at 375px width,
      confirmed visually stacked. Vocabulary table's `.table-scroll`
      wrapper confirmed present around the `<table>` via DOM query.
      Admin list row confirmed wrapping (badge+title on line one,
      Delete button wrapping to line two) via screenshot. Achievement
      cards confirmed showing the 🔒 prefix for locked cards.
- [x] 7.4 At ~768px: confirm the desktop nav row still fits without
      wrapping or overflowing (if it doesn't, note it — the fix is
      changing the breakpoint value, decide then). Verified: at 768px
      the full desktop nav row (Flashcards, Chat practice, Achievements,
      Admin, username, Log out) renders comfortably with no wrapping or
      overflow — 640px breakpoint confirmed correct, no adjustment
      needed.
- [x] 7.5 At desktop width (~1400px): confirm no visual regression
      versus the pre-change appearance (spot-check the header, a lesson
      page, the achievements page). Verified at 1000px (visible-window-
      constrained, still well above the 640px breakpoint): header nav
      row unchanged, no hamburger shown; lesson prev/next confirmed back
      to two equal 366px columns (not accidentally stacked at desktop
      widths) — the media-query-scoped fix doesn't leak outside it.

## 8. Production rollout

- [x] 8.1 Deploy — merge to `main`, CI builds the frontend image,
      `kubectl rollout restart deployment/frontend` (no migration, no
      other service involved). Rolled out successfully.
- [x] 8.2 Spot-check the live site at a mobile viewport: hamburger/
      drawer works, no visual regression at desktop width. Verified
      against production using the same same-origin-iframe technique:
      hamburger shows and the drawer opens correctly at 375px; the
      lesson prev/next fix (the real bug found during local
      verification) is confirmed fixed in production too — a single
      328px column, matching the local result exactly.

## 9. Docs

- [x] 9.1 Update `docs/DEVLOG.md` (session entry) and `docs/ROADMAP.md`
      decision log
