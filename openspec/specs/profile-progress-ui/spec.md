# profile-progress-ui Specification

## Purpose

Gives users a page to view their account info, JLPT level, and streak, and
a leaderboard page to see how their streak compares to other learners —
surfacing progress data that has existed on the backend since M4 but never
had a frontend home.

## Requirements

### Requirement: The profile page displays the user's account info, level, and streak
The frontend SHALL display, on a dedicated profile page, the authenticated
user's username, current JLPT level, and current streak, fetched from the
existing profile and streak endpoints.

#### Scenario: Profile page loads successfully
- **WHEN** an authenticated user opens the profile page
- **THEN** it shows their username, current JLPT level, and current streak
  count

#### Scenario: User with no activity yet
- **WHEN** an authenticated user with no recorded activity opens the
  profile page
- **THEN** their streak is shown as 0, not an error or a blank state

### Requirement: A user can change their JLPT level from the profile page
The frontend SHALL let an authenticated user select a new JLPT level from
the profile page and persist it via the existing level-update endpoint,
reflecting the change immediately without a page reload.

#### Scenario: User changes their level
- **WHEN** a user selects a different JLPT level on the profile page and
  confirms the change
- **THEN** the profile page shows the newly selected level without
  requiring a reload

### Requirement: The leaderboard page shows users ranked by current streak
The frontend SHALL display, on a dedicated leaderboard page, the list of
users ranked by current streak (highest first), fetched from the existing
leaderboard endpoint, with the signed-in user's own row visually
distinguished if they appear in the results.

#### Scenario: Leaderboard loads with the current user present
- **WHEN** an authenticated user opens the leaderboard page and their own
  streak places them within the returned ranking
- **THEN** their row is visually distinguished from the other rows

#### Scenario: Leaderboard loads with the current user absent
- **WHEN** an authenticated user opens the leaderboard page and their own
  streak does not place them within the returned ranking
- **THEN** the leaderboard still renders normally, with no row
  distinguished

#### Scenario: Empty leaderboard
- **WHEN** an authenticated user opens the leaderboard page and no users
  have any recorded streak activity yet
- **THEN** the page shows an empty state, not an error

### Requirement: The profile and leaderboard pages are reachable from site navigation
The frontend SHALL provide a link to the profile page from the site
header, visible to any authenticated user, and the profile page SHALL
link to the leaderboard page.

#### Scenario: Navigating from the header
- **WHEN** an authenticated user is on any page
- **THEN** the site header shows a link to their profile page

#### Scenario: Navigating from profile to leaderboard
- **WHEN** an authenticated user is on the profile page
- **THEN** they can navigate to the leaderboard page via a link on that
  page
