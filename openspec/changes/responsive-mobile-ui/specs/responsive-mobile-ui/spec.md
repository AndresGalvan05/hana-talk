## Purpose

Ensures the app's navigation and layout remain usable at phone-width
viewports, which were never designed against or verified before this
change.

## ADDED Requirements

### Requirement: The header navigation collapses into a drawer on narrow viewports
The frontend SHALL show a hamburger button in place of the full
navigation row when the viewport is narrower than the mobile breakpoint,
and SHALL reveal the navigation as a slide-out drawer when that button
is activated.

#### Scenario: Narrow viewport shows a hamburger instead of the full nav
- **WHEN** the viewport width is below the mobile breakpoint
- **THEN** the navigation links are not shown as a row, and a hamburger
  button is shown instead

#### Scenario: Activating the hamburger opens the drawer
- **WHEN** a user activates the hamburger button
- **THEN** the navigation links become visible in a slide-out drawer

#### Scenario: Wide viewport is unaffected
- **WHEN** the viewport width is at or above the mobile breakpoint
- **THEN** the navigation renders as the existing horizontal row and no
  hamburger button is shown

### Requirement: The mobile navigation drawer closes on navigation or backdrop interaction
The frontend SHALL close the open navigation drawer automatically when
the user navigates to a different route, and SHALL also close it when
the user clicks outside the drawer.

#### Scenario: Selecting a drawer link navigates and closes the drawer
- **WHEN** a user selects a navigation link while the drawer is open
- **THEN** the app navigates to that link's destination and the drawer
  is no longer open

#### Scenario: Clicking outside the drawer closes it without navigating
- **WHEN** the drawer is open and the user clicks outside it
- **THEN** the drawer closes and no navigation occurs

### Requirement: Layout components remain usable at phone-width viewports
The frontend SHALL avoid horizontal overflow of the lesson prev/next
navigation, admin list rows, leaderboard rows, and the lesson vocabulary
table when the viewport is narrower than the mobile breakpoint.

#### Scenario: Lesson prev/next links stack on a narrow viewport
- **WHEN** a lesson page with both a previous and next lesson is viewed
  below the mobile breakpoint
- **THEN** the previous and next links are shown in a single column
  rather than side by side

#### Scenario: Admin and leaderboard rows wrap instead of overflowing
- **WHEN** an admin list row or leaderboard row does not fit the
  available width below the mobile breakpoint
- **THEN** its contents wrap onto additional lines rather than
  overflowing the viewport horizontally

#### Scenario: A wide vocabulary table scrolls within its own container
- **WHEN** the lesson vocabulary table is wider than the viewport below
  the mobile breakpoint
- **THEN** the table scrolls horizontally within its own container
  rather than causing the page itself to overflow

### Requirement: Achievement cards visually distinguish locked and unlocked status beyond opacity
The achievements page SHALL show a distinct icon on each achievement
card indicating whether it is unlocked, in addition to any existing
visual treatment.

#### Scenario: An unlocked achievement shows an unlocked icon
- **WHEN** an achievement has been unlocked
- **THEN** its card title shows an unlocked-status icon

#### Scenario: A locked achievement shows a locked icon
- **WHEN** an achievement has not been unlocked
- **THEN** its card title shows a locked-status icon
