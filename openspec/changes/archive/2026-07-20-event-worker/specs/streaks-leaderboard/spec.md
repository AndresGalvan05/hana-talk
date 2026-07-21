## ADDED Requirements

### Requirement: Lesson completions are recorded as day-granularity activity, idempotently
`event-worker` SHALL record one activity entry per user per UTC calendar
day when it consumes an `exercise.completed` event, regardless of how
many completions occur that day or whether the same event is redelivered.

#### Scenario: First completion of the day
- **WHEN** `event-worker` consumes an `exercise.completed` event for a
  user with no recorded activity for that UTC date
- **THEN** a new activity record is created for that user and date

#### Scenario: Second completion on the same day
- **WHEN** `event-worker` consumes a second `exercise.completed` event for
  the same user on a UTC date that already has an activity record
- **THEN** no duplicate activity record is created

#### Scenario: Redelivered event
- **WHEN** `event-worker` consumes the same `exercise.completed` event
  twice (Kafka at-least-once redelivery)
- **THEN** the second delivery does not create a duplicate activity
  record or otherwise change stored state

### Requirement: Current streak is computed from consecutive activity days
`event-worker` SHALL maintain each user's current streak as the count of
consecutive UTC calendar days with an activity record, ending on the most
recent day with activity, and SHALL reset the streak when a gap of more
than one day occurs between activity days.

#### Scenario: Consecutive-day activity extends the streak
- **WHEN** a user has activity on each of several consecutive days,
  ending with today
- **THEN** their current streak equals the number of consecutive days

#### Scenario: A gap resets the streak
- **WHEN** a user's most recent prior activity day is more than one day
  before a new activity day
- **THEN** the streak resets to 1 as of the new activity day

### Requirement: A per-user streak is readable via an internal API, proxied by core-api
`event-worker` SHALL expose an internal endpoint returning a user's
current streak, and core-api SHALL proxy it at `GET /api/users/me/streak`
for the authenticated user.

#### Scenario: Authenticated user requests their streak
- **WHEN** an authenticated user calls `GET /api/users/me/streak`
- **THEN** core-api calls `event-worker`'s internal streak endpoint for
  that user id and returns the current streak

#### Scenario: User with no activity yet
- **WHEN** an authenticated user with no recorded activity calls
  `GET /api/users/me/streak`
- **THEN** the response reports a streak of 0, not an error

### Requirement: A leaderboard ranked by current streak is readable via an internal API, proxied by core-api
`event-worker` SHALL expose an internal endpoint returning users ranked by
current streak, and core-api SHALL proxy it at `GET /api/leaderboard`.

#### Scenario: Authenticated user requests the leaderboard
- **WHEN** an authenticated user calls `GET /api/leaderboard`
- **THEN** core-api calls `event-worker`'s internal leaderboard endpoint
  and returns users ranked by current streak, highest first

### Requirement: event-worker maintains its own user identity projection from user.registered
`event-worker` SHALL build and maintain a local record of each user's id
and username by consuming `user.registered` events, upserted so that
redelivery does not create duplicates, without querying core-api's
database.

#### Scenario: New registration event
- **WHEN** `event-worker` consumes a `user.registered` event for a user id
  it has not seen before
- **THEN** it stores that user's id and username locally

#### Scenario: Redelivered registration event
- **WHEN** `event-worker` consumes the same `user.registered` event twice
- **THEN** the local record for that user is unchanged (not duplicated)
